package com.lin0721.linmusic.feature.localmusic.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalScanFilterTest {

    private val filter = LocalScanFilter(minDurationSec = 60, excludedFolders = setOf("/sdcard/Recordings"))

    @Test
    fun `短于阈值的音频被过滤`() {
        assertFalse(filter.accepts(59_999L, "/sdcard/Music"))
    }

    @Test
    fun `恰好等于阈值的音频保留`() {
        assertTrue(filter.accepts(60_000L, "/sdcard/Music"))
    }

    @Test
    fun `时长缺失的音频不按时长过滤`() {
        assertTrue(filter.accepts(0L, "/sdcard/Music"))
    }

    @Test
    fun `阈值为0时不过滤任何时长`() {
        assertTrue(LocalScanFilter(0, emptySet()).accepts(1_000L, "/sdcard/Music"))
    }

    @Test
    fun `排除目录下的音频被过滤`() {
        assertFalse(filter.accepts(120_000L, "/sdcard/Recordings"))
    }

    @Test
    fun `排除目录只匹配本层目录不影响子目录`() {
        assertTrue(filter.accepts(120_000L, "/sdcard/Recordings/Music"))
    }

    @Test
    fun `没有文件路径的导入条目不受目录排除影响`() {
        assertTrue(filter.accepts(120_000L, null))
    }

    @Test
    fun `从文件路径取所在目录`() {
        assertEquals("/sdcard/Music", folderPathOf("/sdcard/Music/a.mp3"))
        assertNull(folderPathOf(null))
        assertNull(folderPathOf("a.mp3"))
    }
}
