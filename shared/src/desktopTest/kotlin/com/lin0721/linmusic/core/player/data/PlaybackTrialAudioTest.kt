package com.lin0721.linmusic.core.player.data

import com.lin0721.linmusic.core.download.data.SongDownloadUrlItem
import com.lin0721.linmusic.core.download.data.isTrialAudio
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTrialAudioTest {

    @Test
    fun regularSongWithValidDurationIsNotTrial() {
        val item = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 240_000L
        )
        assertFalse(item.isTrialAudio)
    }

    @Test
    fun voicePromptAndShortClipsAreIdentifiedAsTrial() {
        val promptClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/prompt.mp3",
            time = 15_000L
        )
        assertTrue(promptClip.isTrialAudio)

        val sixtySecondClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/sixty.mp3",
            time = 60_000L
        )
        assertTrue(sixtySecondClip.isTrialAudio)

        val normalLengthClip = SongUrlItem(
            id = 28823239,
            url = "https://music.126.net/normal.mp3",
            time = 60_001L
        )
        assertFalse(normalLengthClip.isTrialAudio)
    }

    @Test
    fun musicRepTsUrlIsIdentifiedAsTrial() {
        val item = SongUrlItem(
            id = 12345,
            url = "https://m701.music.126.net/musicrep-ts/target.mp3",
            time = 180_000L
        )
        assertTrue(item.isTrialAudio)
    }

    @Test
    fun freeTrialInfoAndPrivilegeAreIdentifiedAsTrial() {
        val itemWithInfo = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 180_000L,
            freeTrialInfo = FreeTrialInfo(start = 0, end = 30)
        )
        assertTrue(itemWithInfo.isTrialAudio)

        val itemWithPrivilege = SongUrlItem(
            id = 12345,
            url = "https://music.126.net/song.mp3",
            time = 180_000L,
            freeTrialPrivilege = FreeTrialPrivilege(cannotListenReason = 1)
        )
        assertTrue(itemWithPrivilege.isTrialAudio)
    }

    @Test
    fun blankUrlIsIdentifiedAsTrial() {
        assertTrue(SongUrlItem(id = 12345, url = null).isTrialAudio)
        assertTrue(SongUrlItem(id = 12345, url = "").isTrialAudio)
        assertTrue(SongUrlItem(id = 12345, url = "   ").isTrialAudio)
    }

    @Test
    fun downloadItemTrialCheckAligns() {
        val validItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/download.flac",
            time = 240_000L
        )
        assertFalse(validItem.isTrialAudio)

        val shortItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/download.flac",
            time = 15_000L
        )
        assertTrue(shortItem.isTrialAudio)

        val tsItem = SongDownloadUrlItem(
            id = 12345,
            url = "https://music.126.net/musicrep-ts/download.flac",
            time = 240_000L
        )
        assertTrue(tsItem.isTrialAudio)
    }
}
