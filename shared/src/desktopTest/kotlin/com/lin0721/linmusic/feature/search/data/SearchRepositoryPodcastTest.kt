package com.lin0721.linmusic.feature.search.data

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.cache.testOfflineFallback
import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.feature.podcast.ui.InMemoryPreferencesStore
import com.lin0721.linmusic.feature.search.data.dto.CloudSearchRequest
import com.lin0721.linmusic.feature.search.data.dto.CloudSearchResponse
import com.lin0721.linmusic.feature.search.data.dto.HighQualityPlaylistRequest
import com.lin0721.linmusic.feature.search.data.dto.HighQualityPlaylistResponse
import com.lin0721.linmusic.feature.search.data.dto.HighQualityTagsResponse
import com.lin0721.linmusic.feature.search.data.dto.HotSearchDetailResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchDefaultResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestRequest
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestResponse
import com.lin0721.linmusic.feature.search.data.dto.SearchSuggestWebResponse
import com.lin0721.linmusic.feature.search.data.dto.VoiceSearchRequest
import com.lin0721.linmusic.feature.search.data.dto.VoiceSearchResponse
import com.lin0721.linmusic.core.model.EmptyBody
import com.lin0721.linmusic.feature.search.domain.SearchResultItem
import com.lin0721.linmusic.feature.search.domain.SearchType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryPodcastTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    // 只有电台与节目搜索会被调用，其余接口走到即为测试写错
    private class FakeSearchApi(
        var cloud: CloudSearchResponse? = null,
        var voice: VoiceSearchResponse? = null
    ) : SearchApi {
        var lastCloudRequest: CloudSearchRequest? = null
        var lastVoiceRequest: VoiceSearchRequest? = null

        override suspend fun cloudSearch(body: CloudSearchRequest): CloudSearchResponse {
            lastCloudRequest = body
            return cloud!!
        }

        override suspend fun searchVoices(body: VoiceSearchRequest): VoiceSearchResponse {
            lastVoiceRequest = body
            return voice!!
        }

        override suspend fun getSearchDefaultKeyword(body: EmptyBody): SearchDefaultResponse = error("unused")
        override suspend fun getHotSearchDetail(body: EmptyBody): HotSearchDetailResponse = error("unused")
        override suspend fun getHighQualityTags(body: EmptyBody): HighQualityTagsResponse = error("unused")
        override suspend fun getHighQualityPlaylists(body: HighQualityPlaylistRequest): HighQualityPlaylistResponse = error("unused")
        override suspend fun getSearchSuggest(body: SearchSuggestRequest): SearchSuggestResponse = error("unused")
        override suspend fun getSearchSuggestWeb(body: SearchSuggestRequest): SearchSuggestWebResponse = error("unused")
    }

    private fun repository(api: FakeSearchApi) =
        SearchRepositoryImpl(api, ContentFilter(UserPreferences(InMemoryPreferencesStore())), testOfflineFallback())

    // 取自云搜索 type=1009 的真实响应结构，第二项缺封面
    private fun radioJson(total: Int) = """
        {"result":{"djRadios":[
        {"id":972720617,"dj":{"nickname":"闲聊人"},"name":"M字闲聊","picUrl":"http://p4.music.126.net/a.jpg",
        "desc":"闲聊关于爱情","subCount":26379,"programCount":148,"rcmdText":null,"lastProgramCreateTime":1790093213255},
        {"id":2,"name":"无封面电台","programCount":3}],
        "djRadiosCount":$total},"code":200}
    """.trimIndent()

    // 取自 /eapi/search/voice/get 的真实响应结构，第二项没有主曲目，第三项没有 baseInfo
    private fun voiceJson(hasMore: Boolean, resources: String? = null) = """
        {"code":200,"data":{"resources":[${resources ?: """
        {"resourceId":"2508672662","baseInfo":{"mainSong":{"id":1967240319},"dj":{"nickname":"主播甲"},
        "radio":{"id":9,"name":"某电台","picUrl":"http://p/r.jpg"},"duration":1141394,"createTime":1658848151564,
        "listenerCount":101953,"serialNum":1716894795619,"name":"久违的闲聊","id":2508672662,"coverUrl":"http://p3.music.126.net/c.jpg","description":"剪辑到一半"}},
        {"resourceId":"5","baseInfo":{"name":"没有主曲目","id":5}},
        {"resourceId":"x"}"""}],"totalCount":493,"hasMore":$hasMore}}
    """.trimIndent()

    @Test
    fun `电台搜索走云搜索并映射电台条目`() = runBlocking {
        val api = FakeSearchApi(cloud = json.decodeFromString<CloudSearchResponse>(radioJson(total = 100)))

        val page = repository(api).search("闲聊", SearchType.RADIO, offset = 0, limit = 30).first().getOrThrow()

        assertEquals(1009, api.lastCloudRequest?.type)
        val radio = (page.items.single() as SearchResultItem.RadioItem).radio
        assertEquals(972720617L, radio.id)
        assertEquals("闲聊人", radio.djName)
        assertEquals(148, radio.programCount)
        // 缺封面的电台被丢弃，但原始条数仍按接口返回计，翻页偏移才不会错位
        assertEquals(2, page.rawFetchedCount)
        assertEquals(100, page.totalCount)
        assertTrue(page.hasMore)
    }

    @Test
    fun `电台搜索到底时没有下一页`() = runBlocking {
        val api = FakeSearchApi(cloud = json.decodeFromString<CloudSearchResponse>(radioJson(total = 2)))

        val page = repository(api).search("闲聊", SearchType.RADIO, offset = 0, limit = 30).first().getOrThrow()

        assertFalse(page.hasMore)
    }

    @Test
    fun `节目搜索走独立接口并丢弃无效项`() = runBlocking {
        val api = FakeSearchApi(voice = json.decodeFromString<VoiceSearchResponse>(voiceJson(hasMore = true)))

        val page = repository(api).search("闲聊", SearchType.PROGRAM, offset = 30, limit = 30).first().getOrThrow()

        assertEquals("闲聊", api.lastVoiceRequest?.keyword)
        assertEquals(30, api.lastVoiceRequest?.offset)
        // 云搜索接口不应被调用
        assertEquals(null, api.lastCloudRequest)
        val program = (page.items.single() as SearchResultItem.ProgramItem).program
        assertEquals(2508672662L, program.id)
        // 可播放 id 是 mainSong 的 id
        assertEquals(1967240319L, program.songId)
        assertEquals("某电台", program.radioName)
        assertEquals("主播甲", program.djName)
        // 声音搜索里的 serialNum 是类时间戳大数，不算期号
        assertEquals(0, program.serialNum)
        assertEquals(3, page.rawFetchedCount)
        assertEquals(493, page.totalCount)
        assertTrue(page.hasMore)
    }

    @Test
    fun `节目搜索结果为空时不再翻页`() = runBlocking {
        val api = FakeSearchApi(voice = json.decodeFromString<VoiceSearchResponse>(voiceJson(hasMore = true, resources = "")))

        val page = repository(api).search("闲聊", SearchType.PROGRAM, offset = 0, limit = 30).first().getOrThrow()

        assertTrue(page.items.isEmpty())
        assertFalse(page.hasMore)
    }

    @Test
    fun `搜索类型顺序为原有四类加电台与节目`() {
        assertEquals(
            listOf("单曲", "专辑", "歌手", "歌单", "电台", "节目"),
            SearchType.entries.map { it.label }
        )
    }
}
