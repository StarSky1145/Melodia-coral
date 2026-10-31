package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track

// 歌单曲目分页补全的 id 对齐工具。tracks 经屏蔽歌手过滤、删除歌曲、拖拽排序后条数与 trackIds 不再一一对应，
// 因此“还有哪些没加载”必须按 id 判断，不能用 tracks.size 去截 trackIds

// 详情里 trackIds 中尚未出现在 tracks 里的 id，保持原有顺序
fun pendingTrackIds(detail: PlaylistDetail): List<Long> {
    if (detail.trackIds.isEmpty()) return emptyList()
    val loaded = detail.tracks.mapTo(HashSet()) { it.id }
    return detail.trackIds.map { it.id }.filter { it !in loaded }
}

// 把一批补全结果并入详情：requestedIds 是本批请求的 id，未返回的（被屏蔽歌手过滤或已下架）同时从 trackIds 剔除，
// 使得头部显示的总曲目数与实际可见曲目一致
fun PlaylistDetail.withLoadedTracks(requestedIds: List<Long>, loaded: List<Track>): PlaylistDetail {
    val returned = loaded.mapTo(HashSet()) { it.id }
    val dropped = requestedIds.filterTo(HashSet()) { it !in returned }
    return copy(
        tracks = tracks + loaded,
        trackIds = if (dropped.isEmpty()) trackIds else trackIds.filter { it.id !in dropped }
    )
}
