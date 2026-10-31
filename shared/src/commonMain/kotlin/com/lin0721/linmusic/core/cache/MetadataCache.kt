package com.lin0721.linmusic.core.cache

import com.lin0721.linmusic.core.log.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

private const val TAG = "MetadataCache"
private val UNSAFE_NAME_CHARS = Regex("[^A-Za-z0-9_.-]")

// 歌单、专辑等元数据的离线缓存，按账号分目录，一个 key 对应一个 JSON 文件
class MetadataCache(
    private val rootDir: File,
    private val currentUid: suspend () -> Long
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val writeLock = Mutex()

    suspend fun <T> read(key: String, serializer: KSerializer<T>): T? = withContext(Dispatchers.IO) {
        try {
            val file = fileFor(key)
            if (!file.isFile) return@withContext null
            json.decodeFromString(serializer, file.readText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "读取缓存失败 key=$key", e)
            null
        }
    }

    suspend fun <T> write(key: String, serializer: KSerializer<T>, value: T) {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                try {
                    val target = fileFor(key)
                    target.parentFile?.mkdirs()
                    val temp = File(target.parentFile, "${target.name}.tmp")
                    temp.writeText(json.encodeToString(serializer, value))
                    replace(temp, target)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "写入缓存失败 key=$key", e)
                }
            }
        }
    }

    // 退出登录时清空所有账号的缓存
    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            writeLock.withLock {
                try {
                    rootDir.deleteRecursively()
                } catch (e: Exception) {
                    AppLogger.w(TAG, "清空缓存失败", e)
                }
            }
        }
    }

    private suspend fun fileFor(key: String): File {
        val uidDir = File(rootDir, currentUid().toString())
        return File(uidDir, UNSAFE_NAME_CHARS.replace(key, "_") + ".json")
    }

    // 优先原子替换，文件系统不支持时退化为覆盖式移动
    private fun replace(temp: File, target: File) {
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
