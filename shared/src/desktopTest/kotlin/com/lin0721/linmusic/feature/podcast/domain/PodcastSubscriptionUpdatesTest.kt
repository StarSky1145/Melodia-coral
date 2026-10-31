package com.lin0721.linmusic.feature.podcast.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastSubscriptionUpdatesTest {

    private fun radio(id: Long, lastProgramAt: Long) = PodcastRadio(
        id = id,
        name = "电台$id",
        picUrl = "http://p/$id.jpg",
        programCount = 10,
        subCount = 100,
        djName = "主播",
        lastProgramCreateTimeMs = lastProgramAt
    )

    @Test
    fun `首次见到的电台只补基线不算更新`() {
        val result = PodcastSubscriptionUpdates.check(listOf(radio(1, 5_000)), seen = emptyMap())

        assertTrue(result.updatedRadioIds.isEmpty())
        assertEquals(mapOf(1L to 5_000L), result.baseline)
    }

    @Test
    fun `最新一期晚于已见时间算更新`() {
        val result = PodcastSubscriptionUpdates.check(
            listOf(radio(1, 9_000), radio(2, 5_000)),
            seen = mapOf(1L to 5_000L, 2L to 5_000L)
        )

        assertEquals(setOf(1L), result.updatedRadioIds)
        assertTrue(result.baseline.isEmpty())
    }

    @Test
    fun `没有最新一期时间的电台被忽略`() {
        val result = PodcastSubscriptionUpdates.check(listOf(radio(1, 0)), seen = emptyMap())

        assertTrue(result.updatedRadioIds.isEmpty())
        assertTrue(result.baseline.isEmpty())
    }

    @Test
    fun `已见时间不早于最新一期则无更新`() {
        val result = PodcastSubscriptionUpdates.check(listOf(radio(1, 5_000)), seen = mapOf(1L to 8_000L))

        assertTrue(result.updatedRadioIds.isEmpty())
    }
}
