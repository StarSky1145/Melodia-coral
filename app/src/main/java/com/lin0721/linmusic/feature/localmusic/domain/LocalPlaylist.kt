package com.lin0721.linmusic.feature.localmusic.domain

data class LocalPlaylist(
    val id: Long,
    val name: String,
    val updatedAt: Long,
    // 曲库里不存在或被扫描设置隐藏的曲目不出现在这里
    val tracks: List<LocalTrack>
)

fun <T> resolvePlaylistTracks(orderedUris: List<String>, visibleTracksByUri: Map<String, T>): List<T> =
    orderedUris.mapNotNull { visibleTracksByUri[it] }
