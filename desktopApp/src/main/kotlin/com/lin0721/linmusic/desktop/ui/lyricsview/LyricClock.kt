package com.lin0721.linmusic.desktop.ui.lyricsview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.lin0721.linmusic.core.player.PlaybackController

private const val MAX_AHEAD_MS = 250L

// 播放器位置按事件间隔更新，逐字扫色若直接读它会呈阶梯状；
// 播放中以最近一次位置为锚点按真实时间外推，外推量设上限，进度回退或暂停时立刻回到锚点
internal fun extrapolatedPosition(anchorMs: Long, anchorNanos: Long, nowNanos: Long, playing: Boolean): Long {
    if (!playing) return anchorMs
    val elapsedMs = ((nowNanos - anchorNanos) / 1_000_000L).coerceIn(0L, MAX_AHEAD_MS)
    return anchorMs + elapsedMs
}

private class PositionAnchor {
    var positionMs = Long.MIN_VALUE
    var nanos = 0L
}

// 返回在绘制阶段调用的时钟：只读状态，不触发重组
@Composable
internal fun rememberLyricClock(controller: PlaybackController): () -> Long {
    val position = controller.currentPosition.collectAsState()
    val playing = controller.isPlaying.collectAsState()
    val anchor = remember { PositionAnchor() }
    return {
        val now = System.nanoTime()
        val current = position.value
        if (current != anchor.positionMs) {
            anchor.positionMs = current
            anchor.nanos = now
        }
        extrapolatedPosition(anchor.positionMs, anchor.nanos, now, playing.value)
    }
}
