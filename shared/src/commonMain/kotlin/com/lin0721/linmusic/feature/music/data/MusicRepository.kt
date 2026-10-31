package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleAlbumItem
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.domain.StyleSongPage
import com.lin0721.linmusic.feature.music.domain.StyleSort
import kotlinx.coroutines.flow.Flow

const val STYLE_SONG_PAGE_SIZE = 30

// 「音乐」tab 数据仓储
interface MusicRepository {

    // 曲风列表，含二级子标签（公开接口）；成功后进程内缓存
    fun getStyleList(): Flow<Result<List<MusicStyle>>>

    // 我的曲风偏好，未登录返回空列表
    fun getStylePreferences(): Flow<Result<List<StylePreference>>>

    // 曲风详情
    fun getStyleHead(tagId: Long): Flow<Result<StyleHead>>

    // 曲风下的热门歌单
    fun getStylePlaylists(tagId: Long): Flow<Result<List<StylePlaylistItem>>>

    // 曲风下的单曲，按偏移量翻页
    fun getStyleSongs(tagId: Long, cursor: Int, sort: StyleSort): Flow<Result<StyleSongPage>>

    // 曲风下的专辑
    fun getStyleAlbums(tagId: Long, sort: StyleSort): Flow<Result<List<StyleAlbumItem>>>

    // 曲风下的代表歌手
    fun getStyleArtists(tagId: Long): Flow<Result<List<StyleArtistItem>>>
}
