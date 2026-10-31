package com.lin0721.linmusic.core.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NeteaseLinkParserTest {

    @Test
    fun webHashRouteSong() {
        assertEquals(NeteaseLink.Song(186016), NeteaseLinkParser.parse("https://music.163.com/#/song?id=186016"))
    }

    @Test
    fun songLinkWithExtraQuery() {
        assertEquals(
            NeteaseLink.Song(1901371647),
            NeteaseLinkParser.parse("https://music.163.com/song?id=1901371647&userid=12345&dlt=0846")
        )
    }

    @Test
    fun mobileRoutes() {
        assertEquals(NeteaseLink.Playlist(3778678), NeteaseLinkParser.parse("https://y.music.163.com/m/playlist?id=3778678&creatorId=1"))
        assertEquals(NeteaseLink.Album(18905), NeteaseLinkParser.parse("http://music.163.com/album?id=18905"))
        assertEquals(NeteaseLink.Artist(6452), NeteaseLinkParser.parse("https://music.163.com/#/artist?id=6452"))
        assertEquals(NeteaseLink.Radio(336355127), NeteaseLinkParser.parse("https://music.163.com/#/djradio?id=336355127"))
    }

    @Test
    fun pathStyleId() {
        assertEquals(NeteaseLink.Song(186016), NeteaseLinkParser.parse("https://music.163.com/song/186016/?userid=1"))
    }

    @Test
    fun shareTextWithShortLink() {
        val text = "分享周杰伦的单曲《晴天》: https://163cn.tv/xyz123 (来自@网易云音乐)"
        assertEquals(NeteaseLink.ShortLink("https://163cn.tv/xyz123"), NeteaseLinkParser.parse(text))
    }

    @Test
    fun shareTextWithFullLink() {
        val text = "分享歌单《夜跑》 https://music.163.com/playlist?id=2829883282&userid=1（@网易云音乐）"
        assertEquals(NeteaseLink.Playlist(2829883282), NeteaseLinkParser.parse(text))
    }

    @Test
    fun unrelatedLinksAreIgnored() {
        assertNull(NeteaseLinkParser.parse("https://example.com/song?id=1"))
        assertNull(NeteaseLinkParser.parse("https://music.163.com/#/discover"))
        assertNull(NeteaseLinkParser.parse("https://music.163.com/user/home?id=1"))
        assertNull(NeteaseLinkParser.parse("没有链接"))
        assertNull(NeteaseLinkParser.parse(null))
    }
}
