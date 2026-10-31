package com.lin0721.linmusic.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EmptyBodyInterceptorTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().addInterceptor(EmptyBodyInterceptor()).build()

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun call(body: String): Result<String> = runCatching {
        server.enqueue(MockResponse().setBody(body))
        client.newCall(Request.Builder().url(server.url("/eapi/song/enhance/player/url/v1")).build())
            .execute().use { it.body!!.string() }
    }

    @Test
    fun verificationResponseIsRecognized() {
        val result = call("""{"code":-462,"data":{"actionCode":null,"verifyType":40},"message":"请完成验证操作"}""")
        assertTrue(result.exceptionOrNull() is VerificationRequiredException)
    }

    @Test
    fun verificationResponseWithWhitespaceIsRecognized() {
        assertTrue(call(""" { "code" : -462 , "message":"请完成验证操作"}""").exceptionOrNull() is VerificationRequiredException)
    }

    @Test
    fun normalResponsesPassThrough() {
        assertEquals("""{"code":200,"data":[]}""", call("""{"code":200,"data":[]}""").getOrThrow())
        // -4620 等其他负数码不能误判
        assertEquals("""{"code":-4620}""", call("""{"code":-4620}""").getOrThrow())
        // 字段里出现 -462 但 code 不是 -462
        assertEquals("""{"code":200,"msg":"-462"}""", call("""{"code":200,"msg":"-462"}""").getOrThrow())
    }

    @Test
    fun emptyBodyStillMapsToRiskControl() {
        assertTrue(call("").exceptionOrNull() is ApiException)
    }

    @Test
    fun verificationMapsToActionableBizError() {
        val error = mapToAppError(VerificationRequiredException())
        assertTrue(error.isVerificationRequired())
        assertEquals(VERIFICATION_REQUIRED_MESSAGE, (error as AppError.BizError).rawMsg)
    }
}
