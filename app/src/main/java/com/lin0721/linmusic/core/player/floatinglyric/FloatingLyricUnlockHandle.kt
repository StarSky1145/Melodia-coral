package com.lin0721.linmusic.core.player.floatinglyric

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 隐形解锁热区边长：够手指按住，平时完全看不见 */
val UNLOCK_HANDLE_SIZE = 44.dp

/** 按住多久解锁 */
const val UNLOCK_HOLD_MS = 3_000L

/**
 * 锁定状态下叠在歌词右上角的「隐形」解锁热区。
 *
 * 锁定后歌词窗口整体加了 FLAG_NOT_TOUCHABLE，点击/滑动全部穿透给下层 App，
 * 所以只能靠这一小块独立窗口接收「按住不动」：按住满 3 秒解锁。
 *
 * 平时完全不画东西（只留一个 10% 透明度的小点表示位置），只有按住时才画出进度环读秒，
 * 手指移动或提前松开立即取消。
 */
@Composable
fun FloatingLyricUnlockHandle(onUnlock: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(pressed) {
        if (!pressed) {
            progress.animateTo(0f, tween(120))
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        // 读秒以 delay 为准，进度环只是动画：不能用 withTimeoutOrNull 包住等长动画，
        // 那样超时和动画结束同时到，经常出现「环满了却没解锁」
        val ring = launch { progress.animateTo(1f, tween(UNLOCK_HOLD_MS.toInt(), easing = LinearEasing)) }
        delay(UNLOCK_HOLD_MS)
        ring.cancel()
        progress.snapTo(1f)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onUnlock()
    }

    Box(
        modifier = Modifier
            .size(UNLOCK_HANDLE_SIZE)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) break
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                        }
                    } finally {
                        pressed = false
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (pressed) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 2.5.dp.toPx()
                val inset = stroke / 2 + 2.dp.toPx()
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = Color.White.copy(alpha = 0.25f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Color.White.copy(alpha = 0.9f),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        } else {
            // 极淡的一个点，告诉他这里可以按，但基本看不出来
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            )
        }
    }
}
