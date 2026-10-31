package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PlaybackStateTest {

    @Test
    fun `PlaybackState 默认值与 durationMs 正确初始化`() {
        val defaultState = PlaybackState()
        assertEquals(-1L, defaultState.songId)
        assertEquals("", defaultState.title)
        assertEquals(0L, defaultState.lastPositionMs)
        assertEquals(0L, defaultState.durationMs)

        val customState = PlaybackState(
            songId = 1001L,
            title = "测试歌曲",
            artist = "测试歌手",
            coverUrl = "https://example.com/cover.jpg",
            lastPositionMs = 45000L,
            durationMs = 180000L
        )
        assertEquals(1001L, customState.songId)
        assertEquals(45000L, customState.lastPositionMs)
        assertEquals(180000L, customState.durationMs)
    }

    @Test
    fun `上次曲目转换为 MediaItem 保留 mediaId 与元数据`() {
        val state = PlaybackState(songId = 2002L, title = "歌曲", artist = "歌手", lastPositionMs = 30000L, durationMs = 240000L)
        val mediaItem = state.toRestoredMediaItem()
        assertEquals("2002", mediaItem.mediaId)
        assertEquals("歌曲", mediaItem.mediaMetadata.title)
        assertNotNull(mediaItem.mediaMetadata.extras)
    }
}
