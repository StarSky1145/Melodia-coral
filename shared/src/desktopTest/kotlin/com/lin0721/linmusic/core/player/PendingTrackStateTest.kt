package com.lin0721.linmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingTrackStateTest {

    private fun state() = PendingTrackState<String> { it }

    @Test
    fun `没有待播曲目时显示真实曲目`() {
        assertEquals("a", state().display("a"))
        assertNull(state().display(null))
    }

    @Test
    fun `待播曲目优先于真实曲目显示`() {
        val state = state()
        assertTrue(state.show("b", real = "a", playWhenReady = false))
        assertEquals("b", state.display("a"))
    }

    @Test
    fun `目标与真实曲目相同时不做乐观显示`() {
        val state = state()
        assertFalse(state.show("a", real = "a", playWhenReady = true))
        assertNull(state.pending)
    }

    @Test
    fun `真实曲目到达后待播退场`() {
        val state = state()
        state.show("b", real = "a", playWhenReady = true)

        state.onRealTrack("b")

        assertNull(state.pending)
        assertEquals("b", state.display("b"))
    }

    @Test
    fun `其他真实曲目到达不影响待播显示`() {
        val state = state()
        state.show("b", real = "a", playWhenReady = true)

        state.onRealTrack("c")

        assertEquals("b", state.display("c"))
    }

    @Test
    fun `真实曲目被清空时待播退场`() {
        val state = state()
        state.show("b", real = "a", playWhenReady = true)

        state.onRealTrack(null)

        assertNull(state.pending)
    }

    @Test
    fun `快速连点时后一次覆盖前一次且还原最初的播放意图`() {
        val state = state()
        state.show("b", real = "a", playWhenReady = false)
        state.show("c", real = "a", playWhenReady = true)

        assertEquals("c", state.display("a"))
        assertEquals(false, state.clear())
    }

    @Test
    fun `撤销后回到真实曲目`() {
        val state = state()
        state.show("b", real = "a", playWhenReady = true)

        assertEquals(true, state.clear())
        assertEquals("a", state.display("a"))
    }

    @Test
    fun `没有待播曲目时撤销返回空`() {
        assertNull(state().clear())
    }
}
