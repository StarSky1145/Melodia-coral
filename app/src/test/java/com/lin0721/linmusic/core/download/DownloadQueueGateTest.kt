package com.lin0721.linmusic.core.download

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadQueueGateTest {

    @Test
    fun `同时下载数不超过上限`() = runTest {
        val gate = DownloadQueueGate(maxConcurrent = 2) { emptyMap() }
        var running = 0
        var peak = 0
        val releases = List(5) { CompletableDeferred<Unit>() }
        releases.forEachIndexed { index, release ->
            launch {
                gate.withPermit("w$index") {
                    running++
                    peak = maxOf(peak, running)
                    release.await()
                    running--
                }
            }
        }
        advanceUntilIdle()
        assertEquals(2, running)
        releases.forEach { it.complete(Unit) }
        advanceUntilIdle()
        assertEquals(2, peak)
        assertEquals(0, running)
    }

    @Test
    fun `空出名额时按排队顺序放行`() = runTest {
        val orders = mutableMapOf("busy" to 0L, "a" to 30L, "b" to 10L, "c" to 20L)
        val gate = DownloadQueueGate(maxConcurrent = 1) { ids -> ids.associateWith { orders.getValue(it) } }
        val started = mutableListOf<String>()
        val busyRelease = CompletableDeferred<Unit>()
        launch { gate.withPermit("busy") { busyRelease.await() } }
        advanceUntilIdle()
        listOf("a", "b", "c").forEach { id -> launch { gate.withPermit(id) { started += id } } }
        advanceUntilIdle()
        // 排队期间把 a 调到最前
        orders["a"] = -1L
        busyRelease.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("a", "b", "c"), started)
    }

    @Test
    fun `排队中被取消不占用名额`() = runTest {
        val gate = DownloadQueueGate(maxConcurrent = 1) { emptyMap() }
        val busyRelease = CompletableDeferred<Unit>()
        launch { gate.withPermit("busy") { busyRelease.await() } }
        advanceUntilIdle()
        val waiting = launch { gate.withPermit("cancelled") { error("不应执行") } }
        advanceUntilIdle()
        waiting.cancel()
        advanceUntilIdle()
        busyRelease.complete(Unit)
        advanceUntilIdle()
        var ran = false
        launch { gate.withPermit("next") { ran = true } }
        advanceUntilIdle()
        assertTrue(ran)
    }
}
