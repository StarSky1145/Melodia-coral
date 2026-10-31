package com.lin0721.linmusic.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameRetentionTest {

    @Test
    fun `已出栈且不在组合中的栈帧被释放`() {
        val retention = FrameRetention(8)
        retention.enter(1, evictable = true)
        retention.leave(1)

        assertEquals(listOf(1L), retention.release(liveIds = emptySet()))
        assertTrue(retention.release(liveIds = emptySet()).isEmpty())
    }

    @Test
    fun `仍在组合中的出栈栈帧等离开组合后再释放`() {
        val retention = FrameRetention(8)
        retention.enter(1, evictable = true)

        assertTrue(retention.release(liveIds = emptySet()).isEmpty())

        retention.leave(1)
        assertEquals(listOf(1L), retention.release(liveIds = emptySet()))
    }

    @Test
    fun `仍在栈里的栈帧不释放`() {
        val retention = FrameRetention(8)
        retention.enter(1, evictable = true)
        retention.leave(1)

        assertTrue(retention.release(liveIds = setOf(1L)).isEmpty())
    }

    @Test
    fun `超出上限时淘汰最久未用且不在组合中的栈帧`() {
        val retention = FrameRetention(2)
        (1L..3L).forEach {
            retention.enter(it, evictable = true)
            retention.leave(it)
        }

        assertEquals(listOf(1L), retention.release(liveIds = setOf(1L, 2L, 3L)))
    }

    @Test
    fun `重新访问会刷新最近使用顺序`() {
        val retention = FrameRetention(2)
        (1L..3L).forEach {
            retention.enter(it, evictable = true)
            retention.leave(it)
        }
        retention.enter(1, evictable = true)
        retention.leave(1)

        assertEquals(listOf(2L), retention.release(liveIds = setOf(1L, 2L, 3L)))
    }

    @Test
    fun `组合中的栈帧不会被淘汰`() {
        val retention = FrameRetention(1)
        retention.enter(1, evictable = true)
        retention.enter(2, evictable = true)

        assertTrue(retention.release(liveIds = setOf(1L, 2L)).isEmpty())

        retention.leave(1)
        assertEquals(listOf(1L), retention.release(liveIds = setOf(1L, 2L)))
    }

    @Test
    fun `根栈帧常驻且不占额度`() {
        val retention = FrameRetention(1)
        retention.enter(1, evictable = false)
        retention.leave(1)
        retention.enter(2, evictable = true)
        retention.leave(2)

        assertTrue(retention.release(liveIds = setOf(1L, 2L)).isEmpty())
    }

    @Test
    fun `释放后的栈帧重新进入视为新栈帧`() {
        val retention = FrameRetention(8)
        retention.enter(1, evictable = true)
        retention.leave(1)
        retention.release(liveIds = emptySet())

        retention.enter(1, evictable = true)
        retention.leave(1)
        assertTrue(retention.release(liveIds = setOf(1L)).isEmpty())
    }
}
