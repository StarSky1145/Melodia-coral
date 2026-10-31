package com.lin0721.linmusic.desktop.ui.palette

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverPaletteTest {

    private fun argb(r: Int, g: Int, b: Int, a: Int = 255): Int =
        (a shl 24) or (r shl 16) or (g shl 8) or b

    @Test
    fun `空像素返回兜底色板`() {
        assertEquals(FallbackCoverPalette, extractCoverPalette(IntArray(0)))
    }

    @Test
    fun `全白封面被过滤后返回兜底色板`() {
        assertEquals(FallbackCoverPalette, extractCoverPalette(IntArray(64) { argb(255, 255, 255) }))
    }

    @Test
    fun `红色封面取出偏红的主色`() {
        val palette = extractCoverPalette(IntArray(400) { argb(200, 30, 30) })
        assertTrue(palette.base.red > palette.base.green)
        assertTrue(palette.base.red > palette.base.blue)
    }

    @Test
    fun `灰阶封面的主色不带色调`() {
        val base = extractCoverPalette(IntArray(400) { argb(90, 90, 90) }).base
        assertEquals(base.red, base.green, 0.001f)
        assertEquals(base.green, base.blue, 0.001f)
    }
}
