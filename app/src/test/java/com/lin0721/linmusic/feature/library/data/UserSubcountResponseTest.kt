package com.lin0721.linmusic.feature.library.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSubcountResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    // 取自 /weapi/subcount 真实响应
    private val realJson = """
        {"programCount":0,"djRadioCount":9,"mvCount":3,"artistCount":211,"newProgramCount":27,
        "createDjRadioCount":0,"createdPlaylistCount":20,"subPlaylistCount":25,"code":200}
    """.trimIndent()

    @Test
    fun `真实响应解析出歌手数与MV数`() {
        val response = json.decodeFromString<UserSubcountResponse>(realJson)

        assertTrue(response.isSuccess)
        assertEquals(211, response.artistCount)
        assertEquals(3, response.mvCount)
        assertEquals(20, response.createdPlaylistCount)
        assertEquals(25, response.subPlaylistCount)
    }

    @Test
    fun `歌单数为创建与收藏之和`() {
        val response = json.decodeFromString<UserSubcountResponse>(realJson)
        assertEquals(45, response.playlistCount)
    }

    @Test
    fun `业务错误码不视为成功`() {
        val response = json.decodeFromString<UserSubcountResponse>("""{"code":404}""")

        assertFalse(response.isSuccess)
        assertEquals(0, response.playlistCount)
    }
}
