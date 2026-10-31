package com.lin0721.linmusic.core.player.domain

import kotlin.math.max
import kotlin.math.min

/** 实际命中的行与仍保持高亮、滚动锚点的行分开维护。 */
data class LyricPlaybackState(
    val hotIndices: Set<Int> = emptySet(),
    val displayIndices: Set<Int> = emptySet(),
    val primaryIndex: Int = -1
)

object LyricTimeline {
    /** 与 AMLL core 一样，在载入时处理时间；不在播放进度回调中重复修改歌词。 */
    fun prepareLines(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val prepared = lines.mapIndexed { index, line ->
            val fallbackEnd = lines.getOrNull(index + 1)?.timeMs
            var main = normalizeWordWindow(line)
            if (main.durationMs <= 0) {
                val end = fallbackEnd?.takeIf { it > main.timeMs } ?: main.timeMs + 10_000L
                main = main.retime(main.timeMs, end)
            }
            val background = line.backgroundLine?.let(::normalizeWordWindow)
            if (background != null) {
                val groupStart = min(main.timeMs, background.timeMs)
                val groupEnd = max(main.endMs(), background.endMs())
                main = main.retime(groupStart, groupEnd).copy(
                    backgroundLine = background.retime(groupStart, groupEnd)
                )
            }
            main
        }.toMutableList()

        // 小幅重叠视为标注误差；明显的重叠保留给多行同时播放。
        for (index in 0 until prepared.lastIndex) {
            val line = prepared[index]
            val next = prepared[index + 1]
            val overlap = line.endMs() - next.timeMs
            if (overlap > 0 && !(overlap > 100 && overlap > next.durationMs * 0.1)) {
                prepared[index] = line.retime(line.timeMs, next.timeMs).copy(
                    backgroundLine = line.backgroundLine?.retime(line.timeMs, next.timeMs)
                )
            }
        }

        // 行可提前进入高亮，但词的绝对时间保持不变。
        var previousStart = 0L
        var previousEnd = 0L
        var previousGroupStart = 0L
        var previousGroupEnd = 0L
        prepared.indices.forEach { index ->
            val line = prepared[index]
            val start = line.timeMs
            val end = line.endMs()
            val hasPrevious = index > 0
            val advance = if (!hasPrevious || start >= previousEnd) 600L else 400L
            val boundary = if (!hasPrevious) 0L else if (start >= previousEnd) {
                previousGroupEnd
            } else {
                previousStart + ((previousEnd - previousStart) * 0.3).toLong()
            }
            val newStart = max(boundary, start - advance).coerceAtMost(start)
            if (newStart < start) {
                prepared[index] = line.retime(newStart, end).copy(
                    backgroundLine = line.backgroundLine?.retime(newStart, end)
                )
            }
            if (hasPrevious && start < previousGroupEnd && end > previousGroupStart) {
                previousGroupStart = min(previousGroupStart, start)
                previousGroupEnd = max(previousGroupEnd, end)
            } else {
                previousGroupStart = start
                previousGroupEnd = end
            }
            previousStart = start
            previousEnd = end
        }
        return prepared
    }

    fun activeIndices(lines: List<LyricLine>, positionMs: Long): Set<Int> = buildSet {
        lines.forEachIndexed { index, line ->
            val fallbackEnd = lines.getOrNull(index + 1)?.timeMs ?: Long.MAX_VALUE
            if (line.isActiveAt(positionMs, fallbackEnd) ||
                line.backgroundLine?.isActiveAt(positionMs, fallbackEnd) == true) add(index)
        }
    }

    /** 重叠行的旧行结束后可暂留，直到新行加入或缓冲行全部结束。 */
    fun advance(
        lines: List<LyricLine>,
        positionMs: Long,
        previous: LyricPlaybackState,
        isSeek: Boolean = false
    ): LyricPlaybackState {
        val hot = activeIndices(lines, positionMs)
        val added = hot - previous.hotIndices
        val expired = previous.displayIndices - hot
        val display = when {
            isSeek -> hot
            added.isNotEmpty() -> (previous.displayIndices + added) - expired
            expired.isNotEmpty() && expired == previous.displayIndices -> emptySet()
            else -> previous.displayIndices
        }
        val primary = when {
            display.isNotEmpty() && (isSeek || added.isNotEmpty()) -> display.min()
            isSeek && lines.firstOrNull()?.let { positionMs < it.timeMs } == true -> -1
            isSeek -> lines.indexOfFirst { it.timeMs >= positionMs }
                .takeIf { it >= 0 } ?: lines.lastIndex
            display.isEmpty() && lines.lastOrNull()?.let { positionMs >= it.endMs() } == true -> lines.lastIndex
            else -> previous.primaryIndex
        }
        return LyricPlaybackState(hot, display, primary)
    }

    fun primaryIndex(
        lines: List<LyricLine>,
        positionMs: Long,
        activeIndices: Set<Int>,
        previousPrimary: Int
    ): Int = previousPrimary.takeIf { it in activeIndices }
        ?: activeIndices.minOrNull()
        ?: lines.indexOfLast { it.timeMs <= positionMs }
}

private fun normalizeWordWindow(line: LyricLine): LyricLine {
    if (line.words.isEmpty()) return line
    val first = line.timeMs + line.words.first().startOffsetMs
    val last = line.timeMs + line.words.last().startOffsetMs + line.words.last().durationMs
    return if (last > first) line.retime(first, last) else line
}

private fun LyricLine.retime(start: Long, end: Long): LyricLine = copy(
    timeMs = start,
    durationMs = (end - start).coerceAtLeast(0L),
    words = words.map { it.copy(startOffsetMs = it.startOffsetMs + timeMs - start) }
)

private fun LyricLine.endMs(): Long = if (durationMs > 0 && timeMs <= Long.MAX_VALUE - durationMs) {
    timeMs + durationMs
} else timeMs

fun LyricLine.isActiveAt(positionMs: Long, fallbackEndMs: Long = Long.MAX_VALUE): Boolean {
    val end = if (durationMs > 0) endMs() else fallbackEndMs
    return positionMs >= timeMs && positionMs < end
}
