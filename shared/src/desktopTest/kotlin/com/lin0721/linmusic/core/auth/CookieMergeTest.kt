package com.lin0721.linmusic.core.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class CookieMergeTest {

    @Test
    fun nullExistingBuildsFromHeadersAndDropsAttributes() {
        val merged = mergeCookies(null, listOf("MUSIC_U=abc; Max-Age=1296000; Path=/; HttpOnly", "__csrf=xyz; Path=/"))
        assertEquals("MUSIC_U=abc; __csrf=xyz", merged)
    }

    @Test
    fun sameNameOverridesAndKeepsOtherEntries() {
        val merged = mergeCookies("MUSIC_U=old; os=pc", listOf("MUSIC_U=new; Path=/"))
        assertEquals("MUSIC_U=new; os=pc", merged)
    }

    @Test
    fun emptyValueAndImmediateExpiryAreIgnored() {
        val merged = mergeCookies("MUSIC_U=keep", listOf("MUSIC_U=; Path=/", "__csrf=gone; Max-Age=0", "NMTID=ok; Path=/"))
        assertEquals("MUSIC_U=keep; NMTID=ok", merged)
    }

    @Test
    fun malformedHeadersAreSkipped() {
        assertEquals("a=1", mergeCookies("a=1", listOf("", "justtext; Path=/", "=novalue")))
    }

    @Test
    fun valuesContainingEqualsSignSurvive() {
        assertEquals("token=a=b==", mergeCookies(null, listOf("token=a=b==; Path=/")))
    }
}
