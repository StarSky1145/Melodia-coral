package com.lin0721.linmusic.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityFallbackTest {

    @Test
    fun surroundAndMasterFallBackToHiRes() {
        assertEquals("hires", lowerPlaybackLevel("jymaster"))
        assertEquals("hires", lowerPlaybackLevel("sky"))
        assertEquals("hires", lowerPlaybackLevel("jyeffect"))
    }

    @Test
    fun stepsDownOneTierAtATime() {
        assertEquals("lossless", lowerPlaybackLevel("hires"))
        assertEquals("exhigh", lowerPlaybackLevel("lossless"))
        assertEquals("higher", lowerPlaybackLevel("exhigh"))
        assertEquals("standard", lowerPlaybackLevel("higher"))
        assertNull(lowerPlaybackLevel("standard"))
        assertNull(lowerPlaybackLevel("unknown"))
    }

    @Test
    fun capOnlyLowersQuality() {
        assertEquals("lossless", cappedQuality("jymaster", "lossless"))
        assertEquals("exhigh", cappedQuality("exhigh", "lossless"))
        assertEquals("hires", cappedQuality("hires", null))
        assertEquals("hires", cappedQuality("hires", "bogus"))
    }
}
