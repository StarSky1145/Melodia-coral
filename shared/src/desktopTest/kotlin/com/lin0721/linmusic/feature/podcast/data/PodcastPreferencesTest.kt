package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.core.preferences.PreferencesStores
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressRules
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PodcastPreferencesTest {

    private lateinit var dir: File
    private lateinit var progress: PodcastProgressPreferences
    private lateinit var seen: PodcastSeenPreferences

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("melodia-podcast-prefs").toFile()
        // 两个偏好类共用同一个存储文件，与实际装配一致
        val store = PreferencesStores.get(File(dir, "${PreferencesStores.PODCAST}.preferences_pb"))
        progress = PodcastProgressPreferences(store)
        seen = PodcastSeenPreferences(store)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun entry(songId: Long, position: Long = 10_000, updatedAt: Long = 0) =
        PodcastProgressEntry(songId, "节目$songId", "电台 · 主播", "http://p/$songId.jpg", 1_000_000, position, updatedAt)

    @Test
    fun `进度按最近更新优先并按节目去重`() = runBlocking {
        progress.upsert(entry(1, position = 10_000))
        progress.upsert(entry(2))
        progress.upsert(entry(1, position = 99_000))

        val list = progress.entries.first()
        assertEquals(listOf(1L, 2L), list.map { it.songId })
        assertEquals(99_000L, list.first().positionMs)
    }

    @Test
    fun `进度条目超限时丢弃最旧的`() = runBlocking {
        repeat(PodcastProgressRules.MAX_ENTRIES + 5) { progress.upsert(entry(it.toLong())) }

        val list = progress.entries.first()
        assertEquals(PodcastProgressRules.MAX_ENTRIES, list.size)
        // 最后写入的排在最前，最先写入的已被丢弃
        assertEquals((PodcastProgressRules.MAX_ENTRIES + 4).toLong(), list.first().songId)
        assertTrue(list.none { it.songId == 0L })
    }

    @Test
    fun `移除与清空进度`() = runBlocking {
        progress.upsert(entry(1))
        progress.upsert(entry(2))

        progress.remove(1)
        assertEquals(listOf(2L), progress.entries.first().map { it.songId })

        progress.clear()
        assertTrue(progress.entries.first().isEmpty())
    }

    @Test
    fun `基线只补写尚无记录的电台`() = runBlocking {
        seen.seedBaseline(mapOf(1L to 5_000L))
        seen.seedBaseline(mapOf(1L to 9_000L, 2L to 7_000L))

        assertEquals(mapOf(1L to 5_000L, 2L to 7_000L), seen.seen.first())
    }

    @Test
    fun `已见时间只增不减`() = runBlocking {
        seen.markSeen(1, 8_000)
        seen.markSeen(1, 3_000)
        assertEquals(8_000L, seen.seen.first()[1])

        seen.markSeen(1, 12_000)
        assertEquals(12_000L, seen.seen.first()[1])
    }

    @Test
    fun `无效的已见时间被忽略`() = runBlocking {
        seen.markSeen(1, 0)
        assertTrue(seen.seen.first().isEmpty())
    }

    @Test
    fun `两个偏好类共用存储互不干扰`() = runBlocking {
        progress.upsert(entry(1))
        seen.markSeen(1, 5_000)
        seen.clear()

        assertEquals(1, progress.entries.first().size)
        assertTrue(seen.seen.first().isEmpty())
    }
}
