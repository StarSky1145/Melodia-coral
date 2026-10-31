package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalPlaylistTest {

    @Test
    fun `按歌单顺序还原曲目`() {
        val visible = mapOf("a" to "A", "b" to "B", "c" to "C")
        assertEquals(listOf("C", "A", "B"), resolvePlaylistTracks(listOf("c", "a", "b"), visible))
    }

    @Test
    fun `被隐藏或已不在曲库的曲目跳过`() {
        val visible = mapOf("a" to "A", "c" to "C")
        assertEquals(listOf("A", "C"), resolvePlaylistTracks(listOf("a", "hidden", "c"), visible))
    }

    @Test
    fun `空歌单返回空列表`() {
        assertEquals(emptyList<String>(), resolvePlaylistTracks(emptyList(), mapOf("a" to "A")))
    }
}
