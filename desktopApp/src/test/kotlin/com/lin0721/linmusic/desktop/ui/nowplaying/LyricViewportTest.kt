package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricViewportTest {

    @Test
    fun `折叠两行与展开六行的基础高度`() {
        // 36 * 2 + 14 * 1
        assertEquals(86.dp, lyricViewportHeight(expanded = false, currentHasTranslation = false, extraWrapLines = 0))
        // 36 * 6 + 14 * 5
        assertEquals(286.dp, lyricViewportHeight(expanded = true, currentHasTranslation = false, extraWrapLines = 0))
    }

    @Test
    fun `当前行带翻译加高 24dp，每多折一行加一个行高`() {
        assertEquals(110.dp, lyricViewportHeight(expanded = false, currentHasTranslation = true, extraWrapLines = 0))
        assertEquals(122.dp, lyricViewportHeight(expanded = false, currentHasTranslation = false, extraWrapLines = 1))
    }

    @Test
    fun `折行数为负时不减少高度`() {
        assertEquals(86.dp, lyricViewportHeight(expanded = false, currentHasTranslation = false, extraWrapLines = -2))
    }

    @Test
    fun `当前行最大最亮，越远越小越淡并有下限`() {
        assertEquals(LyricLineLook(1.15f, 1f), lyricLineLook(index = 3, currentIndex = 3))
        val near = lyricLineLook(index = 4, currentIndex = 3)
        assertEquals(0.93f, near.scale, 0.001f)
        assertEquals(0.73f, near.alpha, 0.001f)
        val far = lyricLineLook(index = 20, currentIndex = 3)
        assertEquals(0.85f, far.scale, 0.001f)
        assertEquals(0.4f, far.alpha, 0.001f)
    }

    @Test
    fun `上方与下方的行按同样的距离处理`() {
        assertEquals(lyricLineLook(5, 3), lyricLineLook(1, 3))
    }
}
