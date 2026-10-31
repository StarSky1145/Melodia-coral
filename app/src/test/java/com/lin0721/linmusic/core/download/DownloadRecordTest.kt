package com.lin0721.linmusic.core.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRecordTest {

    private fun record(quality: String, requestedLevel: String = "") = DownloadRecord(
        songId = 1L,
        mediaStoreUri = "content://media/external/audio/media/1",
        quality = quality,
        downloadedAt = 0L,
        fileSize = 0L,
        requestedLevel = requestedLevel
    )

    @Test
    fun `已下载同等音质视为满足`() {
        assertTrue(record("exhigh").satisfies("exhigh"))
    }

    @Test
    fun `已下载更高音质时低档位不再重复下载`() {
        assertTrue(record("lossless").satisfies("standard"))
        assertTrue(record("lossless").satisfies("exhigh"))
    }

    @Test
    fun `请求更高音质时需要重新下载`() {
        assertFalse(record("standard").satisfies("exhigh"))
        assertFalse(record("exhigh", requestedLevel = "exhigh").satisfies("lossless"))
    }

    @Test
    fun `服务端按歌曲上限降级下发时按请求档位判断，避免每次都重下`() {
        assertTrue(record("lossless", requestedLevel = "hires").satisfies("hires"))
        assertFalse(record("lossless", requestedLevel = "hires").satisfies("jymaster"))
    }

    @Test
    fun `旧版本记录没有请求档位时按实际音质判断`() {
        assertFalse(record("lossless").satisfies("hires"))
    }

    @Test
    fun `未知档位只认完全一致`() {
        assertTrue(record("dolby").satisfies("dolby"))
        assertFalse(record("jymaster").satisfies("dolby"))
    }
}
