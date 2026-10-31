package com.lin0721.linmusic.feature.music.ui

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleAlbumItem
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StyleSort

sealed interface StyleDetailUiState {
    data object Loading : StyleDetailUiState

    data class Success(val data: StyleDetailData) : StyleDetailUiState

    data class Error(val message: String) : StyleDetailUiState
}

data class StyleDetailData(
    val tagId: Long,
    // 头图始终取一级曲风，切二级标签不换
    val head: StyleHead?,
    val children: List<MusicStyle> = emptyList(),
    // null 表示「全部」
    val selectedChildId: Long? = null,
    val sort: StyleSort = StyleSort.Hot,
    val songs: List<Track> = emptyList(),
    val nextCursor: Int = 0,
    val hasMoreSongs: Boolean = false,
    val isLoadingMore: Boolean = false,
    // 切二级标签时四段内容一起刷新
    val isContentLoading: Boolean = false,
    // 切排序时只刷新曲目
    val isSongsLoading: Boolean = false,
    val playlists: List<StylePlaylistItem> = emptyList(),
    val albums: List<StyleAlbumItem> = emptyList(),
    val artists: List<StyleArtistItem> = emptyList()
) {
    val activeTagId: Long get() = selectedChildId ?: tagId
}
