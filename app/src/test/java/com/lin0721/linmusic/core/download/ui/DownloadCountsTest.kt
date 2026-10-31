package com.lin0721.linmusic.core.download.ui

import com.lin0721.linmusic.core.download.DownloadTask
import com.lin0721.linmusic.core.download.DownloadTaskMeta
import com.lin0721.linmusic.core.download.DownloadTaskStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadCountsTest {

    private fun task(id: Int, status: DownloadTaskStatus, progress: Int = 0) = DownloadTask(
        meta = DownloadTaskMeta(
            workId = "w$id",
            songId = id.toLong(),
            songName = "歌曲$id",
            artistName = "歌手",
            level = "exhigh",
            createdAt = 0L
        ),
        status = status,
        progress = progress,
        failureReason = null,
        skipped = false
    )

    @Test
    fun `按状态分别计数`() {
        val counts = listOf(
            task(1, DownloadTaskStatus.SUCCEEDED),
            task(2, DownloadTaskStatus.SUCCEEDED),
            task(3, DownloadTaskStatus.FAILED),
            task(4, DownloadTaskStatus.RUNNING, progress = 50),
            task(5, DownloadTaskStatus.WAITING),
            task(6, DownloadTaskStatus.PAUSED)
        ).counts()
        assertEquals(6, counts.total)
        assertEquals(2, counts.succeeded)
        assertEquals(1, counts.failed)
        assertEquals(3, counts.settled)
        assertEquals(2, counts.active)
        assertEquals(1, counts.paused)
        assertEquals(3, counts.unfinished)
        assertEquals(0.5f, counts.runningFraction, 0.0001f)
    }

    @Test
    fun `多个下载中的进度累加，越界进度被截断`() {
        val counts = listOf(
            task(1, DownloadTaskStatus.RUNNING, progress = 30),
            task(2, DownloadTaskStatus.RUNNING, progress = 150)
        ).counts()
        assertEquals(1.3f, counts.runningFraction, 0.0001f)
    }

    @Test
    fun `分类筛选`() {
        val waiting = task(1, DownloadTaskStatus.WAITING)
        val failed = task(2, DownloadTaskStatus.FAILED)
        val paused = task(3, DownloadTaskStatus.PAUSED)
        assertEquals(true, DownloadManagerTab.ACTIVE.matches(waiting))
        assertEquals(true, DownloadManagerTab.ACTIVE.matches(paused))
        assertEquals(false, DownloadManagerTab.ACTIVE.matches(failed))
        assertEquals(true, DownloadManagerTab.FAILED.matches(failed))
    }
}
