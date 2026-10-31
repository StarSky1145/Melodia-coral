package com.lin0721.linmusic.feature.podcast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastProgressRecorderTest {

    private val recorder = PodcastProgressRecorder()

    private fun snap(
        position: Long,
        songId: Long = 1,
        duration: Long = 1_000_000,
        playing: Boolean = true
    ) = PodcastPlaybackSnapshot(
        songId = songId,
        title = "节目$songId",
        subtitle = "电台 · 主播",
        coverUrl = "http://p/$songId.jpg",
        positionMs = position,
        durationMs = duration,
        isPlaying = playing
    )

    @Test
    fun `进度不足阈值不记录`() {
        assertNull(recorder.onSnapshot(snap(0), 1))
        assertNull(recorder.onSnapshot(snap(4_999), 2))
    }

    @Test
    fun `首次达到阈值写入并带上节目快照`() {
        val entry = recorder.onSnapshot(snap(5_000), 123)

        assertNotNull(entry)
        assertEquals(1L, entry!!.songId)
        assertEquals("节目1", entry.title)
        assertEquals("电台 · 主播", entry.subtitle)
        assertEquals(5_000L, entry.positionMs)
        assertEquals(123L, entry.updatedAtMs)
    }

    @Test
    fun `播放中进度前进满步长才再次写入`() {
        recorder.onSnapshot(snap(10_000), 1)

        assertNull(recorder.onSnapshot(snap(14_000), 2))
        assertEquals(15_000L, recorder.onSnapshot(snap(15_000), 3)?.positionMs)
    }

    @Test
    fun `暂停时按更细粒度落盘`() {
        recorder.onSnapshot(snap(10_000), 1)

        val entry = recorder.onSnapshot(snap(12_000, playing = false), 2)
        assertEquals(12_000L, entry?.positionMs)
        // 暂停后进度不动，不重复写
        assertNull(recorder.onSnapshot(snap(12_000, playing = false), 3))
    }

    @Test
    fun `从未播放过的曲目不写入`() {
        // 启动时恢复上次状态：有进度但尚未播放
        assertNull(recorder.onSnapshot(snap(100_000, playing = false), 1))
        assertNull(recorder.onSnapshot(snap(100_000, playing = false), 2))
    }

    @Test
    fun `临近结尾按秒写入并判定听完`() {
        recorder.onSnapshot(snap(980_000), 1)

        val entry = recorder.onSnapshot(snap(991_000), 2)
        assertNotNull(entry)
        assertTrue(entry!!.isFinished)
    }

    @Test
    fun `切歌后陈旧进度被忽略`() {
        recorder.onSnapshot(snap(60_000, songId = 1), 1)

        // 曲目已切到 2，进度仍停在上一首的末位
        assertNull(recorder.onSnapshot(snap(60_000, songId = 2), 2))
        assertNull(recorder.onSnapshot(snap(0, songId = 2), 3))
        assertEquals(2L, recorder.onSnapshot(snap(6_000, songId = 2), 4)?.songId)
    }

    @Test
    fun `切歌后从非零进度续播能正常记录`() {
        recorder.onSnapshot(snap(60_000, songId = 1), 1)

        val entry = recorder.onSnapshot(snap(1_200_000, songId = 2, duration = 3_000_000), 2)
        assertEquals(2L, entry?.songId)
        assertEquals(1_200_000L, entry?.positionMs)
    }

    @Test
    fun `回到非播客状态后重新开始记录`() {
        recorder.onSnapshot(snap(60_000), 1)
        assertNull(recorder.onSnapshot(null, 2))

        // 状态清空后同一首再次播放，应当重新写入
        assertEquals(60_000L, recorder.onSnapshot(snap(60_000), 3)?.positionMs)
    }

    @Test
    fun `听完判定按比例或剩余时长`() {
        fun entry(position: Long, duration: Long) =
            PodcastProgressEntry(1, "t", "s", "c", duration, position, 0)

        assertTrue(entry(950_000, 1_000_000).isFinished)
        assertTrue(entry(595_000, 600_000).isFinished)
        assertFalse(entry(500_000, 1_000_000).isFinished)
        // 时长未知时不判定听完
        assertFalse(entry(500_000, 0).isFinished)
    }

    @Test
    fun `剩余时长与比例不越界`() {
        val over = PodcastProgressEntry(1, "t", "s", "c", 1_000, 2_000, 0)
        assertEquals(0L, over.remainingMs)
        assertEquals(1f, over.fraction, 0f)

        val none = PodcastProgressEntry(1, "t", "s", "c", 0, 500, 0)
        assertEquals(0f, none.fraction, 0f)
    }
}
