package com.lin0721.linmusic.desktop.ui.nowplaying

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// 尺寸与行样式沿用移动端歌词预览卡
val LyricItemSpacing = 14.dp
val LyricItemHeight = 36.dp
private val TranslationExtraHeight = 24.dp
const val COLLAPSED_LYRIC_LINES = 2
const val EXPANDED_LYRIC_LINES = 6

// 视口高度：按显示行数撑开，当前行带翻译或折行时再加高，避免内容被裁
fun lyricViewportHeight(expanded: Boolean, currentHasTranslation: Boolean, extraWrapLines: Int): Dp {
    val lines = if (expanded) EXPANDED_LYRIC_LINES else COLLAPSED_LYRIC_LINES
    val base = LyricItemHeight * lines + LyricItemSpacing * (lines - 1)
    return base + (if (currentHasTranslation) TranslationExtraHeight else 0.dp) + LyricItemHeight * extraWrapLines.coerceAtLeast(0)
}

// 离当前行越远越小越淡，最多按 4 行远计
data class LyricLineLook(val scale: Float, val alpha: Float)

fun lyricLineLook(index: Int, currentIndex: Int): LyricLineLook {
    if (index == currentIndex) return LyricLineLook(scale = 1.15f, alpha = 1f)
    val distance = abs(index - currentIndex).coerceAtMost(4)
    return LyricLineLook(
        scale = (1f - distance * 0.07f).coerceAtLeast(0.85f),
        alpha = (0.85f - distance * 0.12f).coerceAtLeast(0.4f)
    )
}
