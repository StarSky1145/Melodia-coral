package com.lin0721.linmusic.desktop.ui.lyricsview

// 逐字高亮的纯计算部分，移植自移动端 KaraokeLyricRow，与排版结果解耦以便单测

// 单个字词在某一视觉行内的物理坐标与演唱时间窗（相对整行起点）
internal class WordLayout(
    val startMs: Long,
    val endMs: Long,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val lineIndex: Int
)

internal class LineLayout(
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float
)

internal class LyricLayoutInfo(
    val wordLayouts: List<WordLayout>,
    val lineLayouts: List<LineLayout>
)

internal data class KaraokeCharacterBox(val lineIndex: Int, val left: Float, val right: Float)

internal data class KaraokeWordSegment(
    val lineIndex: Int,
    val left: Float,
    val right: Float,
    val startMs: Long,
    val endMs: Long
)

// 每一视觉行的已播范围：clipRight 是实色裁剪右缘（当前演唱行已外扩半个羽化带），
// featherCenterX 非空表示该行是正在演唱的行，渐变中心应对齐到此处
internal data class PlayedSpan(
    val clipRight: Float,
    val featherCenterX: Float?
)

// 一个计时词可能横跨自动换行。按视觉行分段，时间按各段可见字形宽度分配；
// 空格由调用处过滤，避免换行空格的坐标被当成上一行的终点。
internal fun splitTimedWordAcrossLines(
    startMs: Long,
    durationMs: Long,
    characters: List<KaraokeCharacterBox>
): List<KaraokeWordSegment> {
    if (characters.isEmpty()) return emptyList()
    val groups = mutableListOf<MutableList<KaraokeCharacterBox>>()
    characters.forEach { character ->
        if (groups.lastOrNull()?.lastOrNull()?.lineIndex == character.lineIndex) {
            groups.last().add(character)
        } else {
            groups.add(mutableListOf(character))
        }
    }
    val widths = groups.map { group -> group.sumOf { (it.right - it.left).coerceAtLeast(1f).toDouble() } }
    val totalWidth = widths.sum()
    var elapsedWidth = 0.0
    return groups.mapIndexed { index, group ->
        val start = startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        elapsedWidth += widths[index]
        val end = if (index == groups.lastIndex) {
            startMs + durationMs
        } else {
            startMs + (durationMs * elapsedWidth / totalWidth).toLong()
        }
        KaraokeWordSegment(
            lineIndex = group.first().lineIndex,
            left = group.minOf { it.left },
            right = group.maxOf { it.right },
            startMs = start,
            endMs = end
        )
    }
}

// 按播放进度推导每一视觉行的已播右缘与羽化中心。纯计算、无状态读取，可在绘制阶段每帧调用
internal fun computePlayedSpans(
    info: LyricLayoutInfo,
    relativeProgress: Long,
    featherHalfPx: Float
): List<PlayedSpan> = info.lineLayouts.mapIndexed { lineIndex, lineLayout ->
    val lastWordOnLine = info.wordLayouts.lastOrNull { it.lineIndex == lineIndex }
    if (lastWordOnLine != null && relativeProgress >= lastWordOnLine.endMs) {
        // 整行已唱完，直接拉满高亮，不需要羽化
        PlayedSpan(clipRight = lineLayout.right, featherCenterX = null)
    } else {
        var maxRight = lineLayout.left
        var hasActiveWord = false
        info.wordLayouts.forEach { word ->
            if (word.lineIndex == lineIndex) {
                if (relativeProgress >= word.endMs) {
                    maxRight = maxRight.coerceAtLeast(word.right)
                } else if (relativeProgress in word.startMs..word.endMs) {
                    // 在当前唱到的字词内做线性像素插值
                    val ratio = if (word.endMs > word.startMs) {
                        (relativeProgress - word.startMs).toFloat() / (word.endMs - word.startMs)
                    } else {
                        1f
                    }
                    maxRight = maxRight.coerceAtLeast(word.left + (word.right - word.left) * ratio)
                    hasActiveWord = true
                }
            }
        }
        if (hasActiveWord) {
            // 已唱完的字形可能比当前字词的插值边缘更靠右（如重叠的标点时间戳），
            // 羽化始终跟随真正的高亮右缘，避免渐变之后残留一条实色竖线
            PlayedSpan(
                clipRight = (maxRight + featherHalfPx).coerceAtMost(lineLayout.right),
                featherCenterX = maxRight
            )
        } else {
            PlayedSpan(clipRight = maxRight, featherCenterX = null)
        }
    }
}
