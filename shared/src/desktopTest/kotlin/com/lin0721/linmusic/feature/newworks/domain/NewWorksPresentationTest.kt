package com.lin0721.linmusic.feature.newworks.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NewWorksPresentationTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDateTime.of(2026, 10, 1, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun ago(minutes: Long) = now - minutes * 60_000L

    @Test
    fun `不足一分钟显示刚刚`() {
        assertEquals("刚刚", formatRelativeTime(ago(0), now, zone))
    }

    @Test
    fun `不足一小时按分钟`() {
        assertEquals("1 分钟前", formatRelativeTime(ago(1), now, zone))
        assertEquals("59 分钟前", formatRelativeTime(ago(59), now, zone))
    }

    @Test
    fun `不足一天按小时`() {
        assertEquals("1 小时前", formatRelativeTime(ago(60), now, zone))
        assertEquals("14 小时前", formatRelativeTime(ago(14 * 60), now, zone))
        assertEquals("23 小时前", formatRelativeTime(ago(24 * 60 - 1), now, zone))
    }

    @Test
    fun `不足七天按天`() {
        assertEquals("1 天前", formatRelativeTime(ago(24 * 60), now, zone))
        assertEquals("6 天前", formatRelativeTime(ago(7 * 24 * 60 - 1), now, zone))
    }

    @Test
    fun `满七天显示日期`() {
        val published = LocalDateTime.of(2026, 9, 24, 12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("2026-09-24", formatRelativeTime(published, now, zone))
    }

    @Test
    fun `无发布时间返回空串`() {
        assertEquals("", formatRelativeTime(0, now, zone))
    }

    @Test
    fun `曲目摘要带数量与曲名`() {
        val release = NewWorksRelease(
            id = 1, title = "Epicure", coverUrl = "", artistName = "a", isAlbum = true, trackCount = 4, publishTime = 0,
            tracks = listOf("シルム", "Continue", "Euphoria", "No*0.4ngel").mapIndexed { i, name ->
                NewWorksTrack(i.toLong(), name, "a", "")
            }
        )
        assertEquals("4 首歌曲 • シルム • Continue • Euphoria • No*0.4ngel", release.trackSummary())
    }

    @Test
    fun `曲目为空时只剩数量`() {
        val release = NewWorksRelease(1, "t", "", "a", isAlbum = true, trackCount = 12, publishTime = 0)
        assertEquals("12 首歌曲", release.trackSummary())
    }

    @Test
    fun `类型标签区分专辑与单曲`() {
        assertEquals("专辑", NewWorksRelease(1, "t", "", "a", true, 3, 0).typeLabel)
        assertEquals("单曲", NewWorksRelease(1, "t", "", "a", false, 1, 0).typeLabel)
    }
}
