package com.lin0721.linmusic.core.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lin0721.linmusic.core.download.data.DownloadApi
import com.lin0721.linmusic.core.download.data.SongDownloadUrlRequest
import com.lin0721.linmusic.core.download.data.isTrialAudio
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.data.PlaybackRepository
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import android.os.ParcelFileDescriptor
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.content.Context

private const val TAG = "SongDownloadWorker"
private const val MAX_ATTEMPTS = 3

private val LOSSLESS_AND_ABOVE = setOf("lossless", "hires", "jyeffect", "sky", "jymaster")
private val COMPRESSED_ENCODE_TYPES = setOf("mp3", "aac", "m4a")

// 歌曲下载后台任务
class SongDownloadWorker(
    context: Context,
    params: WorkerParameters,
    private val downloadApi: DownloadApi,
    private val downloadPreferences: DownloadPreferences,
    private val settingsPreferences: SettingsPreferences,
    private val notificationHelper: DownloadNotificationHelper,
    private val playbackRepository: PlaybackRepository,
    private val downloadClient: OkHttpClient,
    private val taskStore: DownloadTaskStore,
    private val queueGate: DownloadQueueGate
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_SONG_ID = "song_id"
        const val KEY_SONG_NAME = "song_name"
        const val KEY_ARTIST_NAME = "artist_name"
        const val KEY_ALBUM_NAME = "album_name"
        const val KEY_COVER_URL = "cover_url"
        const val KEY_ALBUM_YEAR = "album_year"
        const val KEY_LEVEL = "level"
        const val KEY_ERROR = "error"
        // 面向用户的失败原因，供下载管理面板展示
        const val KEY_REASON = "reason"
        const val KEY_SKIPPED = "skipped"
        const val KEY_BATCH_TAG = "batch_tag"
        const val KEY_BATCH_LABEL = "batch_label"
        const val KEY_PROGRESS_SONG_NAME = "progress_song_name"
        const val KEY_PROGRESS_PERCENT = "progress_percent"
        // 已拿到下载名额开始下载；WorkManager 中处于 RUNNING 但未开始的任务仍在排队
        const val KEY_PROGRESS_STARTED = "progress_started"

        // 断点续传的原始音频数据，按歌曲与音质固定命名，暂停或中断后保留。
        // 不能把服务端返回的文件大小拼进名字：同一首歌每次取链接拿到的 size 可能不同（转码节点/缓存差异），
        // 一旦变化就会换成一个全新的空文件，已下载的字节全部作废，表现就是「下载完成后又从 0 开始」
        fun partialFile(context: Context, songId: Long, level: String) =
            File(context.cacheDir, "dl_${songId}_${level}.part")

        // 某首歌的全部断点文件，取消下载时清理
        fun partialFilesOf(context: Context, songId: Long): List<File> =
            context.cacheDir.listFiles { file -> file.name.startsWith("dl_${songId}_") && file.name.endsWith(".part") }
                ?.toList()
                .orEmpty()

        fun buildInputData(
            songId: Long,
            songName: String,
            artistName: String,
            level: String,
            albumName: String = "",
            coverUrl: String? = null,
            albumYear: Int = 0,
            batchTag: String? = null,
            batchLabel: String? = null
        ) = workDataOf(
            KEY_SONG_ID to songId,
            KEY_SONG_NAME to songName,
            KEY_ARTIST_NAME to artistName,
            KEY_ALBUM_NAME to albumName,
            KEY_COVER_URL to coverUrl,
            KEY_ALBUM_YEAR to albumYear,
            KEY_LEVEL to level,
            KEY_BATCH_TAG to batchTag,
            KEY_BATCH_LABEL to batchLabel
        )
    }

    private val songId: Long get() = inputData.getLong(KEY_SONG_ID, -1)
    private val songName: String get() = inputData.getString(KEY_SONG_NAME) ?: ""
    private val artistName: String get() = inputData.getString(KEY_ARTIST_NAME) ?: ""
    private val albumName: String get() = inputData.getString(KEY_ALBUM_NAME) ?: ""
    private val coverUrl: String? get() = inputData.getString(KEY_COVER_URL)
    private val albumYear: Int get() = inputData.getInt(KEY_ALBUM_YEAR, 0)
    private val level: String get() = inputData.getString(KEY_LEVEL) ?: "standard"
    private val batchTag: String? get() = inputData.getString(KEY_BATCH_TAG)
    private val batchLabel: String get() = inputData.getString(KEY_BATCH_LABEL) ?: "歌曲"

    override suspend fun doWork(): Result {
        val songId = this.songId
        if (songId <= 0) {
            AppLogger.e(TAG, "下载任务参数缺失 songId=$songId")
            return Result.failure(workDataOf(KEY_ERROR to "参数缺失"))
        }

        // 已下载同等或更高音质时直接跳过，避免重复下载产生多份文件
        val existingRecord = downloadPreferences.findVerifiedRecord(songId)
        if (existingRecord != null && existingRecord.satisfies(level)) {
            AppLogger.i(TAG, "已下载 ${existingRecord.quality}，跳过 songId=$songId level=$level")
            persistState(PersistedTaskState.SUCCEEDED, skipped = true)
            onTerminalSkipped()
            return Result.success(workDataOf(KEY_SKIPPED to true))
        }

        // 并发与排队顺序由闸门统一调度，拿到名额后才真正开始下载
        return queueGate.withPermit(id.toString()) { download(songId, existingRecord) }
    }

    private suspend fun download(songId: Long, existingRecord: DownloadRecord?): Result {
        if (batchTag != "stream_cache") {
            // Android 12+ 后台启动前台服务可能被系统拒绝（ForegroundServiceStartNotAllowedException）。
            // 通知只是展示用，抛出去会白白消耗一次重试，让用户看到「下载完成又从 0 开始」
            runCatching { setForegroundAsync(buildForegroundInfo(0)) }
                .onFailure { AppLogger.w(TAG, "进入前台失败，继续下载 songId=$songId", it) }
            publishProgress(0)
        }

        var localTemp: File? = null
        var partial: File? = null
        // 暂停、系统中断或将要重试时保留断点文件，下次从断点继续
        var keepPartial = false
        // 失败或取消时的清理回调
        var cleanup: (() -> Unit)? = null
        try {
            val response = downloadApi.getSongDownloadUrl(SongDownloadUrlRequest(id = songId, level = level))
            val item = response.data
            val url = item?.url
            if (!response.isSuccess || url.isNullOrBlank()) {
                return fail("获取下载链接失败", "获取下载链接失败，code=${response.code}")
            }
            if (item.isTrialAudio) {
                return fail("该音质仅支持试听，需要 VIP/购买后才能完整下载", "仅试听版本")
            }

            val actualEncodeType = (item.type ?: item.encodeType)?.lowercase()
            // 校验是否因权限不足被静默降级为压缩格式
            if (level in LOSSLESS_AND_ABOVE && actualEncodeType in COMPRESSED_ENCODE_TYPES) {
                return fail("需要更高会员等级才能下载该音质", "音质权限不足")
            }
            val extension = (actualEncodeType ?: "mp3")
            val displayName = "${sanitizeFileName("$artistName - $songName")}.$extension"
            val mimeType = mimeTypeFor(extension)

            val partialFile = partialFile(applicationContext, songId, level)
            partial = partialFile
            val downloadedSize = downloadToFile(partialFile, url, item.size)
            if (downloadedSize == null) {
                // 断流/长度不符时保留已下载字节，下次重试从断点继续而不是整首重下
                keepPartial = true
                return fail("下载中断")
            }

            // 下载完成后改名，标签写入与拷贝在独立文件上进行，断点文件只存原始数据
            val tempFile = File(applicationContext.cacheDir, "dl_${songId}_${System.currentTimeMillis()}.$extension")
            if (!partialFile.renameTo(tempFile)) {
                partialFile.copyTo(tempFile, overwrite = true)
            }
            localTemp = tempFile

            // 写入音频元数据、封面与内嵌歌词
            runCatching { writeTags(tempFile, extension) }
                .onFailure { AppLogger.w(TAG, "写入 ID3/Vorbis 标签失败，跳过 songId=$songId", it) }
            val finalFileSize = tempFile.length()

            val customFolderUri = settingsPreferences.downloadFolderUri.first()
            val finalUri: Uri? = if (customFolderUri != null) {
                val directory = resolveCustomDirectory(customFolderUri)
                if (directory == null) {
                    return fail("自定义下载目录不可用，请到设置里重新选择", "自定义下载目录不可用")
                }
                val reusableDoc = findReusableDocument(directory, displayName)
                if (reusableDoc != null) {
                    // 同名文件覆盖写入，不再生成带时间戳的副本
                    if (copyFileToUri(tempFile, reusableDoc.uri, mode = "wt")) reusableDoc.uri else null
                } else {
                    val finalName = uniqueNameIn(directory, displayName)
                    val doc = runCatching { directory.createFile(mimeType, finalName) }.getOrNull()
                    if (doc == null) {
                        return fail("创建本地文件失败", "SAF createFile 失败")
                    }
                    cleanup = { doc.delete() }
                    if (copyFileToUri(tempFile, doc.uri)) {
                        // 文件已写入成功，后续记录写入即使失败也不能再删掉用户已经拿到的文件
                        cleanup = null
                        doc.uri
                    } else {
                        null
                    }
                }
            } else {
                // Android 9 及以下的 MediaStore 没有 RELATIVE_PATH / IS_PENDING 列，
                // 插入时 provider 取不到 _data 会抛 NullPointerException，
                // 于是「下到 100% → 保存抛异常 → Result.retry 从 0 再来」无限循环
                saveToDefaultDirectory(tempFile, displayName, mimeType, extension)
            }

            if (finalUri == null) {
                cleanup?.invoke()
                return fail("下载中断")
            }

            // 记录实际下发的音质档位；记录写失败只影响「是否显示已下载」，
            // 不能因此让任务失败并回滚已落盘的文件（那正是「下完又从头下、本地不保留」的成因）
            saveRecordWithRetry(
                DownloadRecord(
                    songId = songId,
                    mediaStoreUri = finalUri.toString(),
                    quality = item.level ?: level,
                    downloadedAt = System.currentTimeMillis(),
                    fileSize = finalFileSize,
                    songName = songName,
                    artistName = artistName,
                    requestedLevel = level
                )
            )

            // 升级音质后旧文件已被新记录取代，删除以免残留重复歌曲
            existingRecord?.mediaStoreUri
                ?.takeIf { it != finalUri.toString() }
                ?.let { downloadPreferences.deleteFile(it) }

            persistState(PersistedTaskState.SUCCEEDED)
            onTerminalSuccess()
            return Result.success()
        } catch (e: CancellationException) {
            // 暂停、取消或系统中断：清理目标文件，保留断点文件；用户取消时由管理器删除断点
            cleanup?.invoke()
            if (restorePartial(localTemp, partial)) localTemp = null
            keepPartial = true
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "下载异常 songId=$songId", e)
            cleanup?.invoke()
            if (restorePartial(localTemp, partial)) localTemp = null
            return if (runAttemptCount < MAX_ATTEMPTS) {
                keepPartial = true
                Result.retry()
            } else {
                fail(if (e is IOException) "网络异常，请检查网络后重试" else "下载异常", e.message ?: "下载异常")
            }
        } finally {
            localTemp?.delete()
            if (!keepPartial) partial?.delete()
        }
    }

    // 发送完成通知
    private fun onTerminalSuccess() {
        val tag = batchTag
        if (tag == "stream_cache") return
        if (tag == null) {
            notificationHelper.showSuccess(songId, songName)
            return
        }
        val settledExcludingSelf = batchSettledCount(tag)
        val (_, total) = batchCounts(tag)
        if (settledExcludingSelf + 1 >= total) {
            notificationHelper.showBatchSummary(tag, batchLabel, total)
        }
    }

    // 结束状态写入任务存储，WorkManager 清理已结束任务后面板仍能展示
    private suspend fun persistState(state: PersistedTaskState, failureReason: String? = null, skipped: Boolean = false) {
        runCatching { taskStore.markState(setOf(id.toString()), state, failureReason, skipped) }
            .onFailure { AppLogger.w(TAG, "记录下载任务状态失败 songId=$songId", it) }
    }

    // 已下载跳过时的通知，批量任务计入批次进度
    private fun onTerminalSkipped() {
        val tag = batchTag
        if (tag == null) {
            notificationHelper.showAlreadyDownloaded(songId, songName)
        } else {
            onTerminalSuccess()
        }
    }

    // 终态失败：记录状态、发通知并带上用户可读的原因与排查用的详情
    private suspend fun fail(reason: String, detail: String = reason): Result {
        persistState(PersistedTaskState.FAILED, failureReason = reason)
        onTerminalFailure(reason)
        return Result.failure(workDataOf(KEY_ERROR to detail, KEY_REASON to reason))
    }

    private fun onTerminalFailure(reason: String) {
        val tag = batchTag
        if (tag == "stream_cache") {
            AppLogger.w(TAG, "边听边存后台保存失败: $reason songId=$songId")
            return
        }
        if (tag == null) {
            notificationHelper.showFailed(songId, songName, reason)
            return
        }
        val settledExcludingSelf = batchSettledCount(tag)
        val (_, total) = batchCounts(tag)
        if (settledExcludingSelf + 1 >= total) {
            notificationHelper.showBatchSummary(tag, batchLabel, total)
        }
    }

    // 异步上报下载进度
    private fun publishProgress(progress: Int) {
        setProgressAsync(
            workDataOf(
                KEY_PROGRESS_SONG_NAME to songName,
                KEY_PROGRESS_PERCENT to progress,
                KEY_PROGRESS_STARTED to true
            )
        )
    }

    private fun buildForegroundInfo(progress: Int): ForegroundInfo {
        val tag = batchTag
        val notificationId: Int
        val notification: android.app.Notification
        if (tag != null) {
            val (settled, total) = batchCounts(tag)
            notificationId = notificationHelper.batchNotificationIdFor(tag)
            notification = notificationHelper.buildBatchProgressNotification(batchLabel, settled, total)
        } else {
            notificationId = notificationHelper.notificationIdFor(songId)
            notification = notificationHelper.buildProgressNotification(songName, progress)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun batchSettledCount(tag: String): Int = batchCounts(tag).first

    // 获取批次完成数与总数
    private fun batchCounts(tag: String): Pair<Int, Int> {
        val infos = runCatching {
            WorkManager.getInstance(applicationContext).getWorkInfosByTag(tag).get()
        }.getOrNull() ?: return 0 to 1
        val total = infos.size
        val settled = infos.count { it.state.isFinished }
        return settled to total
    }

    private fun relativePath(): String {
        val folder = batchTag?.let { sanitizeFileName(batchLabel) }
        return if (folder != null) "Music/Melodia/$folder/" else "Music/Melodia/"
    }

    private fun ensureMusicDirectoryExists(folderName: String?) {
        runCatching {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val targetDir = if (folderName != null) File(musicDir, "Melodia/$folderName") else File(musicDir, "Melodia")
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
        }.onFailure { AppLogger.w(TAG, "创建物理目录失败", it) }
    }

    private fun insertPendingMediaStoreEntry(displayName: String, mimeType: String): Uri? {
        val folder = batchTag?.let { sanitizeFileName(batchLabel) }
        ensureMusicDirectoryExists(folder)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath())
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        return applicationContext.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
    }

    // 查找目标目录下可复用的同名 MediaStore 条目
    private suspend fun findReusableMediaStoreEntry(displayName: String): Uri? {
        val uri = withContext(Dispatchers.IO) {
            runCatching {
                applicationContext.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(MediaStore.Audio.Media._ID),
                    "${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
                    arrayOf(relativePath(), displayName),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(0))
                    } else {
                        null
                    }
                }
            }.onFailure { AppLogger.w(TAG, "查询同名文件失败 songId=$songId", it) }.getOrNull()
        } ?: return null
        return uri.takeUnless { downloadPreferences.isUriClaimedByOtherSong(it.toString(), songId) }
    }

    // 查找自定义目录下可复用的同名文件
    private suspend fun findReusableDocument(directory: DocumentFile, displayName: String): DocumentFile? {
        val doc = withContext(Dispatchers.IO) {
            runCatching { directory.findFile(displayName) }.getOrNull()
        }?.takeIf { it.isFile } ?: return null
        return doc.takeUnless { downloadPreferences.isUriClaimedByOtherSong(it.uri.toString(), songId) }
    }

    // 覆盖前标记为写入中；非本应用创建的文件无写权限，返回 false 后改为新建
    private fun markMediaStoreEntryPending(uri: Uri): Boolean = runCatching {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 1) }
        applicationContext.contentResolver.update(uri, values, null, null) > 0
    }.onFailure { AppLogger.w(TAG, "同名文件不可覆盖，改为新建 uri=$uri", it) }.getOrDefault(false)

    private fun finalizePendingMediaStoreEntry(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        applicationContext.contentResolver.update(uri, values, null, null)
    }

    // 保存到默认目录：Android 10+ 走 MediaStore（写公共音乐库），
    // 更低版本或 MediaStore 出错时直写文件，保证下载成功的字节一定落盘
    private suspend fun saveToDefaultDirectory(
        tempFile: File,
        displayName: String,
        mimeType: String,
        extension: String
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { saveToMediaStore(tempFile, displayName, mimeType) }
                .onFailure { AppLogger.w(TAG, "MediaStore 保存失败，改用直写文件 songId=$songId", it) }
                .getOrNull()
                ?.let { return it }
        }
        return saveToFileDirectory(tempFile, displayName, extension)
    }

    // MediaStore 写入：同名条目覆盖写，避免系统自动重命名出 "(1)" 副本
    private suspend fun saveToMediaStore(tempFile: File, displayName: String, mimeType: String): Uri? {
        val reusableUri = findReusableMediaStoreEntry(displayName)
        if (reusableUri != null && markMediaStoreEntryPending(reusableUri)) {
            val written = copyFileToUri(tempFile, reusableUri, mode = "wt")
            runCatching { finalizePendingMediaStoreEntry(reusableUri) }
                .onFailure { AppLogger.w(TAG, "结束写入中状态失败 uri=$reusableUri", it) }
            if (written) return reusableUri
            runCatching { applicationContext.contentResolver.delete(reusableUri, null, null) }
            return null
        }
        val uri = insertPendingMediaStoreEntry(displayName, mimeType) ?: return null
        if (!copyFileToUri(tempFile, uri)) {
            runCatching { applicationContext.contentResolver.delete(uri, null, null) }
            return null
        }
        runCatching { finalizePendingMediaStoreEntry(uri) }
            .onFailure { AppLogger.w(TAG, "结束写入中状态失败 uri=$uri", it) }
        return uri
    }

    // 直写文件：有存储权限就写公共音乐目录，否则退回应用专属目录（不需要任何权限）
    private suspend fun saveToFileDirectory(tempFile: File, displayName: String, extension: String): Uri? =
        withContext(Dispatchers.IO) {
            val folder = batchTag?.let { sanitizeFileName(batchLabel) }
            val subDir = if (folder != null) "Melodia/$folder" else "Melodia"
            val publicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                subDir
            )
            val usePublic = hasStorageWritePermission() &&
                runCatching { publicDir.mkdirs() || publicDir.isDirectory }.getOrDefault(false) &&
                publicDir.canWrite()
            val directory = if (usePublic) {
                publicDir
            } else {
                File(
                    applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                        ?: applicationContext.filesDir,
                    subDir
                ).also { runCatching { it.mkdirs() } }
            }
            val target = resolveTargetFile(directory, displayName)
            val written = runCatching {
                FileOutputStream(target, false).use { out -> tempFile.inputStream().use { it.copyTo(out) } }
            }.onFailure { AppLogger.e(TAG, "写入文件失败 path=${target.absolutePath} songId=$songId", it) }
                .isSuccess
            if (!written) {
                runCatching { target.delete() }
                return@withContext null
            }
            if (usePublic) {
                // 登记到系统媒体库，其他播放器/文件管理器里也能看到这首歌
                runCatching {
                    MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(target.absolutePath),
                        arrayOf(mimeTypeFor(extension)),
                        null
                    )
                }.onFailure { AppLogger.w(TAG, "登记媒体库失败 path=${target.absolutePath}", it) }
            }
            AppLogger.i(TAG, "已保存到 ${target.absolutePath} songId=$songId")
            Uri.fromFile(target)
        }

    // 同名文件没被别的歌占用就复用（覆盖写），否则加时间戳避免顶掉另一首
    private suspend fun resolveTargetFile(directory: File, displayName: String): File {
        val existing = File(directory, displayName)
        if (!existing.exists()) return existing
        val claimedByOther =
            downloadPreferences.isUriClaimedByOtherSong(Uri.fromFile(existing).toString(), songId)
        if (!claimedByOther) return existing
        val dot = displayName.lastIndexOf('.')
        val name = if (dot > 0) {
            "${displayName.substring(0, dot)}_${System.currentTimeMillis()}${displayName.substring(dot)}"
        } else {
            "${displayName}_${System.currentTimeMillis()}"
        }
        return File(directory, name)
    }

    // Android 9 及以下写公共音乐目录需要运行时存储权限；10+ 走 MediaStore 不需要
    private fun hasStorageWritePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            applicationContext.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    // 解析自定义存储目录
    private fun resolveCustomDirectory(customFolderUri: String): DocumentFile? {
        val root = runCatching {
            DocumentFile.fromTreeUri(applicationContext, Uri.parse(customFolderUri))
        }.getOrNull() ?: return null
        if (!root.exists() || !root.canWrite()) return null
        val tag = batchTag ?: return root
        val folderName = sanitizeFileName(batchLabel)
        return root.findFile(folderName)?.takeIf { it.isDirectory }
            ?: root.createDirectory(folderName)
    }

    // 避免文件名冲突
    private fun uniqueNameIn(directory: DocumentFile, displayName: String): String {
        if (directory.findFile(displayName) == null) return displayName
        val dot = displayName.lastIndexOf('.')
        return if (dot > 0) "${displayName.substring(0, dot)}_${System.currentTimeMillis()}${displayName.substring(dot)}"
        else "${displayName}_${System.currentTimeMillis()}"
    }

    // 写入音频元数据、封面与内嵌歌词
    private suspend fun writeTags(file: File, extension: String) {
        val shouldEmbedLyrics = settingsPreferences.downloadLyricsEnabled.first()
        val rawLyrics = if (shouldEmbedLyrics) {
            runCatching { playbackRepository.getRawLyrics(songId).first().getOrNull() }
                .onFailure { AppLogger.w(TAG, "获取歌词失败，跳过 songId=$songId", it) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
            val fd = pfd.dup().detachFd()
            val metadata = TagLib.getMetadata(fd, readPictures = false)
            val propertyMap = HashMap<String, Array<String>>(metadata?.propertyMap ?: emptyMap())
            propertyMap["TITLE"] = arrayOf(songName)
            propertyMap["ARTIST"] = arrayOf(artistName)
            if (albumName.isNotBlank()) {
                propertyMap["ALBUM"] = arrayOf(albumName)
            }
            if (albumYear > 0) {
                propertyMap["DATE"] = arrayOf(albumYear.toString())
            }
            if (!rawLyrics.isNullOrBlank()) {
                propertyMap["LYRICS"] = arrayOf(rawLyrics)
            }
            TagLib.savePropertyMap(pfd.dup().detachFd(), propertyMap)
        }

        coverUrl?.let { url -> fetchCoverBytes(url) }?.let { coverBytes ->
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).use { pfd ->
                val picture = Picture(
                    data = coverBytes,
                    description = "Front Cover",
                    pictureType = "Front Cover",
                    mimeType = "image/jpeg"
                )
                TagLib.savePictures(pfd.dup().detachFd(), arrayOf(picture))
            }
        }
    }

    // 下载内嵌封面
    private fun fetchCoverBytes(url: String): ByteArray? = runCatching {
        val sized = if (url.contains('?')) url else "$url?param=500y500"
        val request = Request.Builder().url(sized).build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }.getOrNull()

    // 流式下载到断点文件；已有部分数据且服务端支持 Range 时从断点续传
    private suspend fun downloadToFile(file: File, url: String, expectedSize: Long): Long? = withContext(Dispatchers.IO) {
        val existing = if (file.exists()) file.length() else 0L
        if (expectedSize > 0 && existing == expectedSize) {
            return@withContext existing
        }
        val resumeFrom = if (expectedSize > 0 && existing in 1 until expectedSize) existing else 0L
        val request = Request.Builder()
            .url(url)
            .apply { if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-") }
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                AppLogger.e(TAG, "下载 HTTP 失败 code=${response.code} songId=$songId")
                return@withContext null
            }
            val body = response.body ?: return@withContext null
            val append = resumeFrom > 0 && response.code == 206 &&
                contentRangeStart(response.header("Content-Range")) == resumeFrom
            if (resumeFrom > 0) {
                AppLogger.i(TAG, if (append) "断点续传 from=$resumeFrom songId=$songId" else "服务端未按断点返回，从头下载 songId=$songId")
            }
            val start = if (append) resumeFrom else 0L
            val bodyLength = body.contentLength()
            val total = if (bodyLength > 0) start + bodyLength else expectedSize
            var written = start
            var lastProgress = -1
            FileOutputStream(file, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val progress = (written * 100 / total).toInt()
                            if (progress != lastProgress) {
                                lastProgress = progress
                                if (batchTag == null) {
                                    // 进度刷新过于频繁时系统可能拒绝前台服务更新，忽略即可，不能中断下载
                                    runCatching { setForegroundAsync(buildForegroundInfo(progress)) }
                                }
                                publishProgress(progress)
                            }
                        }
                    }
                }
            }
            if (total > 0 && written != total) null else written
        }
    }

    // 解析 "bytes 100-199/200" 的起始偏移
    private fun contentRangeStart(header: String?): Long? =
        header?.removePrefix("bytes")?.trim()?.substringBefore('-')?.toLongOrNull()

    // 记录写入失败重试一次：DataStore 偶发 IO 异常不该让整次下载前功尽弃
    private suspend fun saveRecordWithRetry(record: DownloadRecord) {
        repeat(2) { attempt ->
            val result = runCatching { downloadPreferences.addRecord(record) }
            if (result.isSuccess) return
            AppLogger.w(TAG, "写入下载记录失败 attempt=${attempt + 1} songId=${record.songId}", result.exceptionOrNull())
            if (attempt == 0) delay(200)
        }
    }

    // 拷贝/写标签等后续步骤失败时，把已下完的临时文件还原成断点文件；
    // 否则 finally 一删，WorkManager 重试就得从 0 重新下整首歌
    private fun restorePartial(temp: File?, partialFile: File?): Boolean {
        val source = temp ?: return false
        val target = partialFile ?: return false
        if (!source.exists() || target.exists()) return false
        return runCatching { source.renameTo(target) || source.copyTo(target, overwrite = true).exists() }
            .onFailure { AppLogger.w(TAG, "还原断点文件失败", it) }
            .getOrDefault(false)
    }

    // 拷贝文件至目标 Uri
    private suspend fun copyFileToUri(file: File, uri: Uri, mode: String = "w"): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val output = applicationContext.contentResolver.openOutputStream(uri, mode) ?: return@runCatching false
            output.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            true
        }.getOrDefault(false)
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "song_$songId" }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "flac" -> "audio/flac"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> "audio/*"
    }
}
