package com.lin0721.linmusic.feature.artist.data

import com.lin0721.linmusic.core.model.ArtistDetailInfo
import com.lin0721.linmusic.core.model.ArtistInfo
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.artist.domain.ArtistAlbumPage
import com.lin0721.linmusic.feature.artist.domain.ArtistSongsPage
import kotlinx.coroutines.flow.Flow

// 歌手数据仓储（artist 业务域）
interface ArtistRepository {

    fun getArtistDetail(artistId: Long): Flow<Result<ArtistDetailInfo>>

    fun getArtistAlbums(artistId: Long, limit: Int = 10, offset: Int = 0): Flow<Result<ArtistAlbumPage>>

    // 获取艺人粉丝数（每月听众数）
    fun getArtistFansCount(artistId: Long): Flow<Result<Long>>

    // 获取艺人热门歌曲（50首）
    fun getArtistTopSongs(artistId: Long): Flow<Result<List<Track>>>

    // 收藏/关注歌手
    fun subscribeArtist(artistId: Long, subscribe: Boolean): Flow<Result<Unit>>

    // 检查是否已关注歌手
    fun checkArtistFollowed(artistId: Long): Flow<Result<Boolean>>

    fun getSimilarArtists(artistId: Long): Flow<Result<List<ArtistInfo>>>

    // 获取歌手全部歌曲（分页；接口前缀未经真机验证，内部按 eapi 优先/失败回退 weapi）
    fun getArtistAllSongs(artistId: Long, offset: Int = 0, limit: Int = 100, order: String = "hot"): Flow<Result<ArtistSongsPage>>
}
