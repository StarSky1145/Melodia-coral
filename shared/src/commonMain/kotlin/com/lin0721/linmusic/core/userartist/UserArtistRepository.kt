package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.model.ArtistInfo
import kotlinx.coroutines.flow.Flow

// 关注歌手列表仓储（core 共享能力，被 home 的"你最爱的艺人"与 library 的歌手聚合复用）
interface UserArtistRepository {

    // 获取已关注的歌手（全部分页；未关注任何人时为空列表，请求失败时为 Result.failure）
    fun getFavoriteArtists(): Flow<Result<List<ArtistInfo>>>
}
