package com.lin0721.linmusic.core.cache

import com.lin0721.linmusic.core.network.AppError
import com.lin0721.linmusic.core.network.OnlineStateProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

// 在线时请求成功即写缓存；仅在检测到离线时改读缓存，在线请求失败不回退
class OfflineFallback(
    private val cache: MetadataCache,
    private val onlineState: OnlineStateProvider
) {
    fun <T> cached(key: String, serializer: KSerializer<T>, source: () -> Flow<Result<T>>): Flow<Result<T>> = flow {
        if (onlineState.isOnline()) {
            source().collect { result ->
                result.onSuccess { cache.write(key, serializer, it) }
                emit(result)
            }
        } else {
            val hit = cache.read(key, serializer)
            emit(if (hit != null) Result.success(hit) else Result.failure(AppError.NetworkError))
        }
    }

    inline fun <reified T> cached(key: String, noinline source: () -> Flow<Result<T>>): Flow<Result<T>> =
        cached(key, serializer<T>(), source)
}
