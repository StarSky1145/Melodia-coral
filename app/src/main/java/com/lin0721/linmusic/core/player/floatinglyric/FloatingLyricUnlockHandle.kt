package com.lin0721.linmusic.core.player.floatinglyric

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 小锁直径：够手指按住，又不至于盖住歌词 */
val UNLOCK_HANDLE_SIZE = 40.dp

/** 按住多久解锁 */
const val UNLOCK_HOLD_MS = 3_000L

/**
 * 锁定状态下叠在歌词右上角的小锁。
 *
 * 锁定后歌词窗口整体加了 FLAG_NOT_TOUCHABLE，点击/滑动全部穿透给下层 App，
 * 所以只能靠这个独立的小窗口接收「按住不动」：按住 3 秒（有进度环读秒）就解锁，
 * 手指一移动或提前松开就取消，不会打断用户正在进行的操作。
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
        // 读秒以 delay 为准，进度环只是动画：不能用 withTimeoutOrNull 包住动画，
        // 动画时长和超时都是 3000ms，谁先到不确定，超时先到就会「进度满了却没解锁」。
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
            .background(Color.Black.copy(alpha = if (pressed) 0.55f else 0.3f), CircleShape)
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
        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.fillMaxSize().padding(1.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.22f),
            strokeWidth = 2.5.dp,
            strokeCap = StrokeCap.Round
        )
        Icon(
            Icons.Rounded.Lock,
            contentDescription = "按住 3 秒解锁悬浮歌词",
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
