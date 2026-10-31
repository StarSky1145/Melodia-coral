package com.lin0721.linmusic.core.userartist

import com.lin0721.linmusic.core.cache.testOfflineFallback
import com.lin0721.linmusic.core.model.Artist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// 手写 Fake 代替真实网络请求
private class FakeUserArtistApi(
    private val sublist: (ArtistSublistRequest) -> ArtistSublistResponse = { error("not used in this test") }
) : UserArtistApi {
    override suspend fun getArtistSublist(body: ArtistSublistRequest): ArtistSublistResponse = sublist(body)
}

class UserArtistRepositoryImplTest {

    private fun artist(id: Long, name: String) = Artist(id = id, name = name, picUrl = "pic/$id", img1v1Url = "avatar/$id")

    @Test
    fun `返回已关注歌手列表`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { _ -> ArtistSublistResponse(code = 200, data = listOf(artist(1, "已关注歌手"))) }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals(listOf("已关注歌手"), result.getOrNull()?.map { it.name })
    }

    @Test
    fun `未关注任何歌手时返回空列表而不是热门歌手`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { _ -> ArtistSublistResponse(code = 200, data = emptyList()) }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals(emptyList<Any>(), result.getOrNull())
    }

    @Test
    fun `接口异常且无任何数据时返回失败`() = runBlocking {
        val api = FakeUserArtistApi(sublist = { _ -> throw RuntimeException("网络异常") })

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `业务码非200且无数据时返回失败`() = runBlocking {
        val api = FakeUserArtistApi(sublist = { _ -> ArtistSublistResponse(code = 301) })

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `优先使用已关注歌手的头像信息(img1v1Url优先于picUrl)`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { _ ->
                ArtistSublistResponse(
                    code = 200,
                    data = listOf(Artist(id = 1, name = "歌手", picUrl = "fallback.jpg", img1v1Url = "primary.jpg"))
                )
            }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertEquals("primary.jpg", result.getOrNull()?.first()?.avatarUrl)
    }

    @Test
    fun `已关注歌手头像为空时回退使用picUrl`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { _ ->
                ArtistSublistResponse(
                    code = 200,
                    data = listOf(Artist(id = 1, name = "歌手", picUrl = "fallback.jpg", img1v1Url = ""))
                )
            }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertEquals("fallback.jpg", result.getOrNull()?.first()?.avatarUrl)
    }

    @Test
    fun `已关注歌手超过一页时分页拉取全部`() = runBlocking {
        val total = 250
        val requests = mutableListOf<ArtistSublistRequest>()
        val api = FakeUserArtistApi(
            sublist = { req ->
                requests += req
                val page = (req.offset until minOf(req.offset + req.limit, total)).map { artist(it.toLong(), "歌手$it") }
                ArtistSublistResponse(code = 200, data = page, hasMore = req.offset + req.limit < total)
            }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertEquals(total, result.getOrNull()?.size)
        assertEquals(listOf(0, 100, 200), requests.map { it.offset })
    }

    @Test
    fun `翻页中途失败时保留已取到的歌手`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { req ->
                if (req.offset == 0) {
                    ArtistSublistResponse(code = 200, data = (1L..100L).map { artist(it, "歌手$it") }, hasMore = true)
                } else {
                    throw RuntimeException("第二页失败")
                }
            }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertTrue(result.isSuccess)
        assertEquals(100, result.getOrNull()?.size)
    }

    @Test
    fun `服务端忽略offset重复返回同一页时不会死循环`() = runBlocking {
        val api = FakeUserArtistApi(
            sublist = { _ ->
                ArtistSublistResponse(code = 200, data = (1L..100L).map { artist(it, "歌手$it") }, hasMore = true)
            }
        )

        val result = UserArtistRepositoryImpl(api, testOfflineFallback()).getFavoriteArtists().first()

        assertEquals(100, result.getOrNull()?.size)
    }
}
