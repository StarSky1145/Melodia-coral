package com.lin0721.linmusic.feature.newworks.ui

import com.lin0721.linmusic.feature.newworks.domain.NewWorksRelease

// 卡片按钮的同步状态：是否已在音乐库、是否正在播放
data class NewWorksReleaseStatus(
    val libraryAlbumIds: Set<Long> = emptySet(),
    val likedSongIds: Set<Long> = emptySet(),
    val nowPlayingSongId: Long? = null,
    val isPlaying: Boolean = false
) {
    // 专辑看是否已收藏，单曲看是否已喜欢
    fun isInLibrary(release: NewWorksRelease): Boolean =
        if (release.isAlbum) release.id in libraryAlbumIds else release.id in likedSongIds

    // 当前播放曲目属于该发布，且处于播放中
    fun isReleasePlaying(release: NewWorksRelease): Boolean =
        isPlaying && containsNowPlaying(release)

    internal fun containsNowPlaying(release: NewWorksRelease): Boolean {
        val current = nowPlayingSongId ?: return false
        return release.tracks.any { it.id == current }
    }
}
