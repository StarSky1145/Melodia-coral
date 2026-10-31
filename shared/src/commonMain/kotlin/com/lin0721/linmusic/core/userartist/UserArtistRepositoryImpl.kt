package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.cache.OfflineFallback
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val TAG = "UserArtistRepositoryImpl"

// 翻页上限，防止服务端异常时无限请求
private const val MAX_SUBLIST_PAGES = 50

class UserArtistRepositoryImpl(
    private val apiService: UserArtistApi,
    private val offline: OfflineFallback
) : UserArtistRepository {

    override fun getFavoriteArtists(): Flow<Result<List<ArtistInfo>>> =
        offline.cached("favorite_artists") { remoteFavoriteArtists() }

    private fun remoteFavoriteArtists(): Flow<Result<List<ArtistInfo>>> = flow {
        val collected = LinkedHashMap<Long, ArtistInfo>()
        var failure: Throwable? = null

        // 已关注歌手（实际返回: {"data":[...], "hasMore":true, "code":200}），分页取全
        // 注意：emit 必须放在 try/catch 之外，避免 .first() 等短路算子的取消信号被 catch 误捕获
        try {
            var offset = 0
            for (page in 0 until MAX_SUBLIST_PAGES) {
                val response = apiService.getArtistSublist(
                    ArtistSublistRequest(limit = ARTIST_SUBLIST_PAGE_SIZE, offset = offset)
                )
                if (response.code != 200) {
                    failure = AppError.BizError(response.code, null)
                    break
                }
                if (response.data.isEmpty()) break
                val before = collected.size
                response.data.forEach { dto ->
                    collected.getOrPut(dto.id) {
                        ArtistInfo(
                            id = dto.id,
                            name = dto.name,
                            avatarUrl = dto.img1v1Url.takeIf { it.isNotBlank() } ?: dto.picUrl
                        )
                    }
                }
                offset += response.data.size
                val hasMore = response.hasMore ?: (response.data.size >= ARTIST_SUBLIST_PAGE_SIZE)
                // 无新增条目说明服务端忽略了 offset，避免死循环
                if (!hasMore || collected.size == before) break
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "获取已关注歌手失败，已取到 ${collected.size} 条", e)
            failure = e
        }

        // 翻页中途失败时保留已取到的部分；一条都没取到才视为失败。未关注任何歌手返回空列表
        if (collected.isEmpty() && failure != null) {
            emit(Result.failure(failure))
        } else {
            emit(Result.success(collected.values.toList()))
        }
    }
}
