package com.lin0721.linmusic.core.cache

import com.lin0721.linmusic.core.network.AppError
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OfflineFallbackTest {

    private lateinit var dir: File
    private var uid = 1L

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-offline-test").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun cache() = testMetadataCache(dir) { uid }

    private fun fallback(online: FakeOnlineState, cache: MetadataCache = cache()) = testOfflineFallback(online, cache)

    @Test
    fun `在线成功后写入缓存，离线时读到同一份数据`() = runBlocking {
        val online = FakeOnlineState(true)
        val fallback = fallback(online)

        val live = fallback.cached("songs") { flow { emit(Result.success(listOf("a", "b"))) } }.first()
        assertEquals(listOf("a", "b"), live.getOrNull())

        online.set(false)
        val offline = fallback.cached<List<String>>("songs") { error("离线时不应发起请求") }.first()
        assertEquals(listOf("a", "b"), offline.getOrNull())
    }

    @Test
    fun `离线且无缓存返回网络错误`() = runBlocking {
        val fallback = fallback(FakeOnlineState(false))

        val result = fallback.cached<List<String>>("none") { error("离线时不应发起请求") }.first()

        assertTrue(result.exceptionOrNull() is AppError.NetworkError)
    }

    @Test
    fun `在线请求失败不回退缓存且不覆盖旧缓存`() = runBlocking {
        val online = FakeOnlineState(true)
        val fallback = fallback(online)
        fallback.cached("songs") { flow { emit(Result.success(listOf("old"))) } }.first()

        val failed = fallback.cached<List<String>>("songs") {
            flow { emit(Result.failure(AppError.NetworkError)) }
        }.first()
        assertTrue(failed.isFailure)

        online.set(false)
        val offline = fallback.cached<List<String>>("songs") { error("离线时不应发起请求") }.first()
        assertEquals(listOf("old"), offline.getOrNull())
    }

    @Test
    fun `不同账号的缓存互相隔离`() = runBlocking {
        val online = FakeOnlineState(true)
        val fallback = fallback(online)
        uid = 1L
        fallback.cached("songs") { flow { emit(Result.success(listOf("账号1"))) } }.first()

        uid = 2L
        online.set(false)
        val result = fallback.cached<List<String>>("songs") { error("离线时不应发起请求") }.first()

        assertTrue(result.isFailure)
    }

    @Test
    fun `缓存文件损坏时读取返回空`() = runBlocking {
        val cache = cache()
        cache.write("songs", ListSerializer(String.serializer()), listOf("a"))
        File(dir, "1").listFiles()!!.single { it.name.endsWith(".json") }.writeText("{损坏")

        assertNull(cache.read("songs", ListSerializer(String.serializer())))
    }

    @Test
    fun `清空缓存后读取返回空`() = runBlocking {
        val cache = cache()
        cache.write("songs", ListSerializer(String.serializer()), listOf("a"))

        cache.clearAll()

        assertNull(cache.read("songs", ListSerializer(String.serializer())))
    }
}
