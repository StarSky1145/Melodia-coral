package com.lin0721.linmusic.feature.podcast.domain

import kotlinx.serialization.Serializable
import kotlin.math.abs

object PodcastProgressRules {
    // 听到末尾这一比例或剩余不足阈值即视为听完
    const val FINISHED_RATIO = 0.95
    const val FINISHED_REMAINING_MS = 10_000L

    // 进度不足此值不记录，避免点开即退的节目占位
    const val MIN_RECORD_POSITION_MS = 5_000L

    // 播放中进度每前进这么多落盘一次
    const val WRITE_STEP_MS = 5_000L

    // 暂停与临近结尾时的落盘粒度
    const val FINE_WRITE_STEP_MS = 1_000L

    const val MAX_ENTRIES = 100
}

// 一期节目的本地收听进度，同时带够渲染「继续收听」所需的快照
@Serializable
data class PodcastProgressEntry(
    val songId: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAtMs: Long,
    // 所属电台 id，旧记录没有这个字段时为 0
    val radioId: Long = 0
) {
    val isFinished: Boolean
        get() = durationMs > 0 && (
            positionMs >= durationMs * PodcastProgressRules.FINISHED_RATIO ||
                durationMs - positionMs < PodcastProgressRules.FINISHED_REMAINING_MS
            )

    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0L)

    // 已听比例，0 到 1
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

// 播客播放状态的一帧快照
data class PodcastPlaybackSnapshot(
    val songId: Long,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val radioId: Long = 0
)

// 决定何时把进度落盘的纯逻辑，不碰 IO 与时钟，便于测试
class PodcastProgressRecorder {

    private var songId: Long? = null
    private var hasPlayed = false
    private var lastWrittenMs = -1L
    private var lastSeenPositionMs = 0L

    // 切歌后进度流可能晚于曲目更新，位置仍等于上一首的末位时视为陈旧帧
    private var stalePositionMs: Long? = null

    // 返回需要落盘的条目，无需写入时返回 null
    fun onSnapshot(snapshot: PodcastPlaybackSnapshot?, nowMs: Long): PodcastProgressEntry? {
        if (snapshot == null) {
            reset()
            return null
        }
        if (snapshot.songId != songId) {
            stalePositionMs = if (songId != null) lastSeenPositionMs else null
            songId = snapshot.songId
            hasPlayed = false
            lastWrittenMs = -1L
        }
        val stale = stalePositionMs
        if (stale != null) {
            if (snapshot.positionMs == stale) return null
            stalePositionMs = null
        }
        lastSeenPositionMs = snapshot.positionMs

        if (snapshot.isPlaying) hasPlayed = true
        // 未真正播放过的曲目不写，避免启动恢复上次状态时刷新排序
        if (!hasPlayed) return null
        if (snapshot.positionMs < PodcastProgressRules.MIN_RECORD_POSITION_MS) return null

        val moved = abs(snapshot.positionMs - lastWrittenMs)
        val nearEnd = snapshot.durationMs > 0 &&
            snapshot.durationMs - snapshot.positionMs < PodcastProgressRules.FINISHED_REMAINING_MS
        val shouldWrite = when {
            lastWrittenMs < 0 -> true
            !snapshot.isPlaying || nearEnd -> moved >= PodcastProgressRules.FINE_WRITE_STEP_MS
            else -> moved >= PodcastProgressRules.WRITE_STEP_MS
        }
        if (!shouldWrite) return null

        lastWrittenMs = snapshot.positionMs
        return PodcastProgressEntry(
            songId = snapshot.songId,
            title = snapshot.title,
            subtitle = snapshot.subtitle,
            coverUrl = snapshot.coverUrl,
            durationMs = snapshot.durationMs,
            positionMs = snapshot.positionMs,
            updatedAtMs = nowMs,
            radioId = snapshot.radioId
        )
    }

    private fun reset() {
        songId = null
        hasPlayed = false
        lastWrittenMs = -1L
        lastSeenPositionMs = 0L
        stalePositionMs = null
    }
}
