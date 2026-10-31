package com.lin0721.linmusic.feature.localmusic.data.tags

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

private const val TAG = "LocalTagEditor"

data class LocalTagSnapshot(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val lyrics: String = "",
    val coverBytes: ByteArray? = null,
    val fileName: String = "",
    val formatLabel: String = ""
)

sealed interface LocalTagCoverChange {
    data object Keep : LocalTagCoverChange
    data object Remove : LocalTagCoverChange
    data class Replace(val bytes: ByteArray) : LocalTagCoverChange
}

data class LocalTagForm(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val trackNumber: String = "",
    val lyrics: String = "",
    val cover: LocalTagCoverChange = LocalTagCoverChange.Keep
)

sealed interface LocalTagWriteResult {
    data object Success : LocalTagWriteResult
    data class NeedsUserConsent(val intentSender: IntentSender) : LocalTagWriteResult
    data object NeedsLegacyStoragePermission : LocalTagWriteResult
    data object NeedsReauthorize : LocalTagWriteResult
    data class Failed(val message: String) : LocalTagWriteResult
}

class LocalTagEditor(private val context: Context) {
    suspend fun read(uri: Uri): LocalTagSnapshot? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var fileName = ""
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) {
                    fileName = it.getString(0) ?: ""
                }
            }
        }
        val formatLabel = fileName.substringAfterLast('.', "").uppercase()

        runCatching {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = true) ?: return@use null
                val props = meta.propertyMap
                
                val title = props["TITLE"]?.joinToString(" / ") ?: ""
                val artist = props["ARTIST"]?.joinToString(" / ") ?: ""
                val album = props["ALBUM"]?.joinToString(" / ") ?: ""
                val albumArtist = props["ALBUMARTIST"]?.joinToString(" / ") ?: ""
                val year = props["DATE"]?.joinToString(" / ") ?: ""
                val trackNumber = props["TRACKNUMBER"]?.joinToString(" / ") ?: ""
                val lyrics = props["LYRICS"]?.joinToString("\n") ?: ""
                val coverBytes = meta.pictures.firstOrNull()?.data

                LocalTagSnapshot(title, artist, album, albumArtist, year, trackNumber, lyrics, coverBytes, fileName, formatLabel)
            }
        }.onFailure { AppLogger.w(TAG, "标签读取失败 uri=$uri", it) }.getOrNull()
    }

    suspend fun write(uri: Uri, source: LocalTrackSource, form: LocalTagForm): LocalTagWriteResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && source != LocalTrackSource.IMPORTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            return@withContext LocalTagWriteResult.NeedsLegacyStoragePermission
        }

        try {
            val pfd = resolver.openFileDescriptor(uri, "rw")
                ?: return@withContext LocalTagWriteResult.Failed("无法打开文件")
            pfd.use {
                // TagLib 每次调用都会关闭传入的 fd，读写各自 dup 一份
                val existing = TagLib.getMetadata(it.dup().detachFd(), readPictures = false)?.propertyMap ?: emptyMap()
                if (!TagLib.savePropertyMap(it.dup().detachFd(), buildTagPropertyMap(existing, form))) {
                    return@withContext LocalTagWriteResult.Failed("写入失败，文件格式可能不支持")
                }
                when (val cover = form.cover) {
                    is LocalTagCoverChange.Replace ->
                        TagLib.savePictures(it.dup().detachFd(), arrayOf(Picture(cover.bytes, "Front Cover", "Front Cover", "image/jpeg")))
                    LocalTagCoverChange.Remove -> TagLib.savePictures(it.dup().detachFd(), emptyArray())
                    LocalTagCoverChange.Keep -> Unit
                }
            }
            LocalTagWriteResult.Success
        } catch (e: SecurityException) {
            // RecoverableSecurityException 是 SecurityException 子类，必须在这里分辨
            when {
                source == LocalTrackSource.IMPORTED -> LocalTagWriteResult.NeedsReauthorize
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    LocalTagWriteResult.NeedsUserConsent(MediaStore.createWriteRequest(resolver, listOf(uri)).intentSender)
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    LocalTagWriteResult.NeedsUserConsent(e.userAction.actionIntent.intentSender)
                else -> {
                    AppLogger.w(TAG, "标签写入无权限 uri=$uri", e)
                    LocalTagWriteResult.Failed("没有写入权限")
                }
            }
        } catch (e: FileNotFoundException) {
            if (source == LocalTrackSource.IMPORTED) {
                LocalTagWriteResult.NeedsReauthorize
            } else {
                AppLogger.w(TAG, "标签写入找不到文件 uri=$uri", e)
                LocalTagWriteResult.Failed("找不到该文件")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "标签写入失败 uri=$uri", e)
            LocalTagWriteResult.Failed("写入失败")
        }
    }

    fun takeWritePermission(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }
}

internal fun buildTagPropertyMap(existing: Map<String, Array<String>>, form: LocalTagForm): HashMap<String, Array<String>> {
    val map = HashMap(existing)
    listOf(
        "TITLE" to form.title,
        "ARTIST" to form.artist,
        "ALBUM" to form.album,
        "ALBUMARTIST" to form.albumArtist,
        "DATE" to form.year,
        "TRACKNUMBER" to form.trackNumber,
        "LYRICS" to form.lyrics
    ).forEach { (key, raw) ->
        val value = raw.trim()
        if (value.isEmpty()) map.remove(key) else map[key] = arrayOf(value)
    }
    return map
}

internal fun validateTagForm(form: LocalTagForm): String? {
    if (form.title.trim().isEmpty()) return "标题不能为空"
    val year = form.year.trim()
    if (year.isNotEmpty()) {
        val y = year.toIntOrNull()
        if (y == null || y !in 1..9999) return "年份格式不正确"
    }
    val trackNum = form.trackNumber.trim()
    if (trackNum.isNotEmpty()) {
        val numPart = trackNum.substringBefore('/')
        val num = numPart.toIntOrNull()
        if (num == null || num <= 0) return "音轨号格式不正确"
    }
    return null
}
