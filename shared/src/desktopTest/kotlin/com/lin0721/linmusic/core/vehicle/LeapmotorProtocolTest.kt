package com.lin0721.linmusic.core.vehicle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeapmotorProtocolTest {

    @Test
    fun `按键码映射与文档一致，未知码返回空`() {
        assertEquals(SteeringKey.PREVIOUS, SteeringKey.fromCode(1))
        assertEquals(SteeringKey.NEXT, SteeringKey.fromCode(4))
        assertEquals(SteeringKey.PLAY_PAUSE, SteeringKey.fromCode(7))
        assertNull(SteeringKey.fromCode(0))
        assertNull(SteeringKey.fromCode(2))
    }

    @Test
    fun `窗口内同键只放行第一次，边界值 400ms 仍视为重复`() {
        val d = SteeringKeyDebouncer(400)
        assertTrue(d.accept(SteeringKey.NEXT, 1_000))
        assertFalse(d.accept(SteeringKey.NEXT, 1_005))
        assertFalse(d.accept(SteeringKey.NEXT, 1_400))
        assertTrue(d.accept(SteeringKey.NEXT, 1_401))
    }

    @Test
    fun `不同键互不影响`() {
        val d = SteeringKeyDebouncer(400)
        assertTrue(d.accept(SteeringKey.NEXT, 1_000))
        assertTrue(d.accept(SteeringKey.PREVIOUS, 1_010))
    }

    @Test
    fun `去重以最近放行时刻为基准，不会因持续到达而永久吞键`() {
        val d = SteeringKeyDebouncer(400)
        assertTrue(d.accept(SteeringKey.PLAY_PAUSE, 0))
        assertFalse(d.accept(SteeringKey.PLAY_PAUSE, 300))
        assertTrue(d.accept(SteeringKey.PLAY_PAUSE, 450))
    }

    @Test
    fun `前台门：在前台或正在播放才声明`() {
        assertTrue(shouldClaimMediaSource(selfOnTop = true, playWhenReady = false))
        assertTrue(shouldClaimMediaSource(selfOnTop = false, playWhenReady = true))
        assertFalse(shouldClaimMediaSource(selfOnTop = false, playWhenReady = false))
    }

    @Test
    fun `前台判定要求包名后紧跟斜杠，避免前缀相同的包误判`() {
        val pkg = "com.lin0721.linmusic"
        assertTrue(isSelfOnTop("$pkg/$pkg.MainActivity", pkg))
        assertFalse(isSelfOnTop("$pkg.debug/$pkg.MainActivity", pkg))
        assertFalse(isSelfOnTop("", pkg))
        assertFalse(isSelfOnTop(null, pkg))
    }

    @Test
    fun `媒体源声明保留厂商原版字段：type 15 与 imagUrl`() {
        assertEquals(
            """{"type":"music","data":{"type":15,"name":"晴天","title":"晴天","artist":"周杰伦","duration":0,"imagUrl":"","state":1}}""",
            buildMediaInfoJson("晴天", "周杰伦", playing = true)
        )
        assertTrue(buildMediaInfoJson("a", "b", playing = false).endsWith(""""state":0}}"""))
    }

    @Test
    fun `歌名中的引号反斜杠与换行被转义`() {
        val json = buildMediaInfoJson("a\"b\\c\nd", "x\ty\u0001", playing = true)
        assertTrue(json.contains(""""title":"a\"b\\c\nd""""))
        assertTrue(json.contains(""""artist":"x\ty\u0001""""))
    }
}
