package com.lin0721.linmusic.feature.localmusic.data.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalTagEditorTest {

    @Test
    fun `保留无关键`() {
        val existing = mapOf("UNKNOWN" to arrayOf("VALUE"))
        val form = LocalTagForm()
        val result = buildTagPropertyMap(existing, form)
        assertEquals("VALUE", result["UNKNOWN"]?.get(0))
    }

    @Test
    fun `空值删除键`() {
        val existing = mapOf("TITLE" to arrayOf("Old Title"))
        val form = LocalTagForm(title = "   ")
        val result = buildTagPropertyMap(existing, form)
        assertNull(result["TITLE"])
    }

    @Test
    fun `写入前去掉首尾空白`() {
        val result = buildTagPropertyMap(emptyMap(), LocalTagForm(title = "  New Title  "))
        assertEquals("New Title", result["TITLE"]?.get(0))
    }

    @Test
    fun `七个键都写入`() {
        val form = LocalTagForm(
            title = "A", artist = "B", album = "C", albumArtist = "D", year = "2023", trackNumber = "1", lyrics = "G"
        )
        val result = buildTagPropertyMap(emptyMap(), form)
        assertEquals("A", result["TITLE"]?.get(0))
        assertEquals("B", result["ARTIST"]?.get(0))
        assertEquals("C", result["ALBUM"]?.get(0))
        assertEquals("D", result["ALBUMARTIST"]?.get(0))
        assertEquals("2023", result["DATE"]?.get(0))
        assertEquals("1", result["TRACKNUMBER"]?.get(0))
        assertEquals("G", result["LYRICS"]?.get(0))
    }

    @Test
    fun `标题空`() {
        val form = LocalTagForm(title = "   ")
        assertEquals("标题不能为空", validateTagForm(form))
    }

    @Test
    fun `年份非法`() {
        val form = LocalTagForm(title = "A", year = "abcd")
        assertEquals("年份格式不正确", validateTagForm(form))
        
        val form2 = LocalTagForm(title = "A", year = "10000")
        assertEquals("年份格式不正确", validateTagForm(form2))
    }

    @Test
    fun `年份合法`() {
        val form = LocalTagForm(title = "A", year = " 2023 ")
        assertNull(validateTagForm(form))
    }

    @Test
    fun `音轨号 3 slash 12 合法`() {
        val form = LocalTagForm(title = "A", trackNumber = "3/12")
        assertNull(validateTagForm(form))
    }

    @Test
    fun `0 与 abc 非法`() {
        val form0 = LocalTagForm(title = "A", trackNumber = "0")
        assertEquals("音轨号格式不正确", validateTagForm(form0))
        
        val formAbc = LocalTagForm(title = "A", trackNumber = "abc")
        assertEquals("音轨号格式不正确", validateTagForm(formAbc))
    }

    @Test
    fun `全部合法返回 null`() {
        val form = LocalTagForm(title = "A", year = "2023", trackNumber = "1/10")
        assertNull(validateTagForm(form))
    }
}
