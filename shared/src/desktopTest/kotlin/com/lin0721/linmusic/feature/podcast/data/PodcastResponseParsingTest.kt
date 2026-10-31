package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.feature.podcast.domain.toPodcastCategoryGroups
import com.lin0721.linmusic.feature.podcast.domain.toPodcastRadios
import com.lin0721.linmusic.feature.podcast.domain.toPodcastRankedPrograms
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastResponseParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    // 取自 /eapi/djradio/get/subed 真机响应，保留真实字段结构并精简内容
    private val subscribedJson = """
        {"count":1,"djRadios":[{"dj":{"nickname":"理理理理喵","avatarUrl":"http://p1.music.126.net/a.jpg"},
        "category":"创作翻唱","lastProgramName":"Op.96（无名曲子）","subCount":3225,
        "picUrl":"https://p2.music.126.net/NTsR_/1.jpg","lastProgramId":3723275554,"programCount":12,
        "lastProgramCreateTime":1782569283598,"name":"理理自用纯音乐","id":1224033046,"rcmdtext":null,
        "newProgramCount":37,"descPicList":[{"type":1,"id":0,"nestedData":{"textList":[{"text":"x"}]}}]}],
        "time":1,"hasMore":false,"code":200}
    """.trimIndent()

    // 取自 /eapi/djradio/home/category/recommend 真机响应，推荐语字段为 rcmdText
    private val categoryGroupJson = """
        {"code":200,"msg":null,"data":[
        {"categoryId":3,"categoryName":"情感","radios":[{"id":526564706,"name":"伴听FM","rcmdText":"用小半的我筑温暖的你",
        "picUrl":"https://p1.music.126.net/s.jpg","programCount":18,"subCount":0,"subed":false,"playCount":6553205,
        "lastProgramName":"想和你把生活过成一首诗"}]},
        {"categoryId":9,"categoryName":"没有电台的分组","radios":[]},
        {"categoryId":0,"categoryName":"无id","radios":[{"id":1,"name":"a","picUrl":"http://p/1.jpg"}]}]}
    """.trimIndent()

    private val hotJson = """
        {"djRadios":[{"dj":{"nickname":"柠檬心理课堂"},"category":"知识","lastProgramName":"做不到也没关系",
        "subCount":380000,"programCount":1593,"picUrl":"https://p3.music.126.net/h.jpg",
        "lastProgramId":3709868743,"lastProgramCreateTime":1766912400000,"name":"五分钟心理学","id":349714286,
        "rcmdtext":"每天5分钟，了解自己透析人性"}],"hasMore":true,"count":2,"code":200}
    """.trimIndent()

    // 取自 /eapi/program/toplist/v1 真机响应，第二项故意缺 program
    private val programToplistJson = """
        {"updateTime":1791165601491,"toplist":[
        {"program":{"mainSong":{"name":"x","id":3442988111},"dj":{"nickname":"主播甲"},
        "radio":{"id":9,"name":"某电台","picUrl":"http://p/r.jpg","dj":{"nickname":"主播乙"}},
        "duration":208533,"listenerCount":305174,"serialNum":495,"coverUrl":"https://p4.music.126.net/c.jpg",
        "description":"Battle\nCp10","createTime":1790829309858,"name":"[凝视之下] Rendezvous","id":3729406022},
        "rank":1,"lastRank":2,"score":74113},
        {"rank":2,"lastRank":2,"score":1}],"code":200}
    """.trimIndent()

    @Test
    fun `订阅列表解析出最新一期信息`() {
        val response = json.decodeFromString<PodcastSubscribedResponse>(subscribedJson)

        assertTrue(response.isSuccess)
        assertFalse(response.hasMore)
        val radio = response.djRadios.toPodcastRadios().single()
        assertEquals(1224033046L, radio.id)
        assertEquals("理理自用纯音乐", radio.name)
        assertEquals(12, radio.programCount)
        assertEquals(3225L, radio.subCount)
        assertEquals("理理理理喵", radio.djName)
        assertEquals("Op.96（无名曲子）", radio.lastProgramName)
        assertEquals(1782569283598L, radio.lastProgramCreateTimeMs)
        assertEquals("", radio.recommendText)
    }

    @Test
    fun `最新一期时间为null时按默认值处理`() {
        val response = json.decodeFromString<PodcastSubscribedResponse>(
            """{"djRadios":[{"id":1,"name":"a","picUrl":"http://p/1.jpg","lastProgramCreateTime":null,"lastProgramName":null}],"code":200}"""
        )

        val radio = response.djRadios.toPodcastRadios().single()
        assertEquals(0L, radio.lastProgramCreateTimeMs)
        assertEquals("", radio.lastProgramName)
    }

    @Test
    fun `分类分组取rcmdText并丢弃无效分组`() {
        val response = json.decodeFromString<PodcastCategoryGroupResponse>(categoryGroupJson)

        assertTrue(response.isSuccess)
        val groups = response.data.toPodcastCategoryGroups()
        // 空电台分组与无 id 分组被丢弃
        assertEquals(listOf("情感"), groups.map { it.categoryName })
        val radio = groups.single().radios.single()
        assertEquals("伴听FM", radio.name)
        assertEquals("用小半的我筑温暖的你", radio.recommendText)
        assertEquals("想和你把生活过成一首诗", radio.lastProgramName)
    }

    @Test
    fun `分类热门电台解析分页标记与推荐语`() {
        val response = json.decodeFromString<PodcastCategoryHotResponse>(hotJson)

        assertTrue(response.hasMore)
        val radio = response.djRadios.toPodcastRadios().single()
        assertEquals("每天5分钟，了解自己透析人性", radio.recommendText)
        assertEquals(1766912400000L, radio.lastProgramCreateTimeMs)
    }

    @Test
    fun `节目榜保持顺序并跳过缺失program的项`() {
        val response = json.decodeFromString<PodcastProgramToplistResponse>(programToplistJson)

        assertTrue(response.isSuccess)
        assertEquals(2, response.toplist.size)
        val programs = response.toplist.toPodcastRankedPrograms()
        val first = programs.single()
        assertEquals(3729406022L, first.id)
        // 可播放 id 是 mainSong 的 id
        assertEquals(3442988111L, first.songId)
        assertEquals("某电台", first.radioName)
        assertEquals("主播甲", first.djName)
        assertEquals(495, first.serialNum)
    }

    @Test
    fun `节目简介压平换行`() {
        val response = json.decodeFromString<PodcastProgramToplistResponse>(programToplistJson)

        assertEquals("Battle Cp10", response.toplist.toPodcastRankedPrograms().single().description)
    }

    @Test
    fun `非200响应判定为失败`() {
        val response = json.decodeFromString<PodcastSubscribedResponse>("""{"code":301,"msg":"需要登录"}""")

        assertFalse(response.isSuccess)
        assertTrue(response.djRadios.isEmpty())
    }
}
