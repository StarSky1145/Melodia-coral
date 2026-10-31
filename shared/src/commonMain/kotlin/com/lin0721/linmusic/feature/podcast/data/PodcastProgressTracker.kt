package com.lin0721.linmusic.feature.podcast.data

import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.domain.PodcastPlaybackSnapshot
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressRecorder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch

private const val TAG = "PodcastProgressTracker"
private const val SAMPLE_INTERVAL_MS = 1_000L

// 监听播放器，把播客节目的收听进度写入本地
class PodcastProgressTracker(
    private val controller: PlaybackController,
    private val preferences: PodcastProgressPreferences
) {

    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job = scope.launch {
        val recorder = PodcastProgressRecorder()
        val podcastItem = combine(controller.currentQueueItem, controller.playContext) { item, context ->
            item.takeIf { context == PlaybackController.CONTEXT_PODCAST }
        }
        combine(
            podcastItem,
            controller.currentPosition,
            controller.duration,
            controller.isPlaying
        ) { item, position, duration, playing ->
            item?.let {
                PodcastPlaybackSnapshot(
                    songId = it.songId,
                    title = it.title,
                    subtitle = it.artist,
                    coverUrl = it.coverUrl,
                    positionMs = position,
                    durationMs = duration,
                    isPlaying = playing,
                    radioId = it.radioId
                )
            }
        }
            // 切歌瞬间进度与曲目的更新有先后，按秒采样可滤掉这类瞬时状态，同时起到节流作用
            .sample(SAMPLE_INTERVAL_MS)
            .collect { snapshot ->
                val entry = recorder.onSnapshot(snapshot, System.currentTimeMillis()) ?: return@collect
                try {
                    preferences.upsert(entry)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.w(TAG, "收听进度写入失败 songId=${entry.songId}", e)
                }
            }
    }
}
