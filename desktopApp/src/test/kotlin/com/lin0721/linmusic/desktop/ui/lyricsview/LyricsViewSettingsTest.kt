package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.player.domain.LyricAlignment
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsViewSettingsTest {

    @Test
    fun `居中时所有声部居中`() {
        assertEquals(RowAlignment.Center, effectiveAlignment("center", LyricAlignment.START))
        assertEquals(RowAlignment.Center, effectiveAlignment("center", LyricAlignment.END))
    }

    @Test
    fun `左对齐时主声部靠左，后声部靠右`() {
        assertEquals(RowAlignment.Start, effectiveAlignment("left", LyricAlignment.START))
        assertEquals(RowAlignment.End, effectiveAlignment("left", LyricAlignment.END))
    }

    @Test
    fun `右对齐时主声部与后声部互换位置`() {
        assertEquals(RowAlignment.End, effectiveAlignment("right", LyricAlignment.START))
        assertEquals(RowAlignment.Start, effectiveAlignment("right", LyricAlignment.END))
    }

    @Test
    fun `未知对齐值按左对齐处理`() {
        assertEquals(RowAlignment.Start, effectiveAlignment("other", LyricAlignment.START))
    }

    @Test
    fun `副行选项只列有数据的项，仅原词始终可选`() {
        assertEquals(listOf("none"), secondaryOptions(hasTranslation = false, hasRoma = false).map { it.first })
        assertEquals(listOf("translation", "none"), secondaryOptions(hasTranslation = true, hasRoma = false).map { it.first })
        assertEquals(listOf("translation", "roma", "none"), secondaryOptions(hasTranslation = true, hasRoma = true).map { it.first })
    }

    @Test
    fun `副行与背景和声字号随主字号按比例缩放`() {
        val settings = LyricsViewSettings(fontSize = 40)
        assertEquals(22.sp, settings.secondaryFontSize)
        assertEquals(28.sp, settings.backgroundFontSize)
        assertEquals(18.sp, settings.backgroundSecondaryFontSize)
    }

    @Test
    fun `取值范围覆盖默认值`() {
        val defaults = LyricsViewSettings()
        assertEquals(true, defaults.fontSize in LyricsFontSizeRange)
        assertEquals(true, defaults.lineSpacing in LyricsLineSpacingRange)
        assertEquals(true, defaults.secondarySpacing in LyricsSecondarySpacingRange)
    }
}
