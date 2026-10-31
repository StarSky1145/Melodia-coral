package com.lin0721.linmusic.feature.localmusic.ui

import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack

enum class LocalMusicSortOrder(val label: String) {
    DATE_DESC("最近添加"), NAME_ASC("按名称"), SIZE_DESC("按大小")
}

fun sortTracks(tracks: List<LocalTrack>, order: LocalMusicSortOrder): List<LocalTrack> = when (order) {
    LocalMusicSortOrder.DATE_DESC -> tracks.sortedByDescending { it.dateAddedMs }
    LocalMusicSortOrder.NAME_ASC -> tracks.sortedBy { it.title }
    LocalMusicSortOrder.SIZE_DESC -> tracks.sortedByDescending { it.sizeBytes }
}
