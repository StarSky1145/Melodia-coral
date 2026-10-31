package com.lin0721.linmusic.feature.playlist.ui

import com.lin0721.linmusic.feature.home.data.DailySong
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.core.userplaylist.UserPlaylist

// 歌单/专辑详情页 UI 状态
sealed interface PlaylistUiState {
    data object Loading : PlaylistUiState

    data class Success(
        val playlist: PlaylistDetail,
        val recommendedSongs: List<Track> = emptyList(),
        val isSubscribed: Boolean = false,
        // 尚未补全的曲目 id（按歌单顺序）。按 id 记录而非用 tracks.size 对齐，
        // 因为屏蔽歌手过滤、删歌、排序都会让 tracks 条数与 trackIds 脱节
        val pendingTrackIds: List<Long> = emptyList(),
        val isLoadingMoreTracks: Boolean = false,
        val trackPlayCounts: Map<Long, Int> = emptyMap()
    ) : PlaylistUiState {
        val hasMoreTracks: Boolean get() = pendingTrackIds.isNotEmpty()
    }
    data class Error(val message: String) : PlaylistUiState
}

// 历史日推（每日推荐/听歌排行）的浏览状态，独立于页面主加载态
data class HistoryRecommendState(
    val dates: List<String> = emptyList(),
    val datesLoading: Boolean = false,
    val songs: List<DailySong> = emptyList(),
    val selectedDate: String? = null,
    val songsLoading: Boolean = false
)

// "添加到歌单"批量导入目标选择的浏览状态
data class PlaylistImportState(
    val items: List<UserPlaylist> = emptyList(),
    val isLoading: Boolean = false
)
