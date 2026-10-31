package com.lin0721.linmusic.core.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "ExternalInterruptionResumeController"
// 短视频切换防抖延时
private const val DEBOUNCE_DELAY_MS = 1500L
// 打断等待超时阈值（10 分钟）
private const val MAX_INTERRUPTION_WINDOW_MS = 10 * 60 * 1000L
// 用户主动暂停后的异步焦点丢失竞态保护时间窗
private const val PAUSE_GRACE_PERIOD_MS = 800L

enum class UserPlaybackIntent {
    PLAYING,
    PAUSED_BY_USER
}

private data class InterruptionSession(
    val songId: Long?,
    val interruptionTimestamp: Long
)

// 外部音视频打断与自动恢复控制器
class ExternalInterruptionResumeController(
    private val context: Context,
    private val settingsPreferences: SettingsPreferences
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var scope: CoroutineScope? = null
    private var onResumeRequested: (() -> Unit)? = null

    @Volatile
    private var isEnabled: Boolean = true

    @Volatile
    private var userIntent: UserPlaybackIntent = UserPlaybackIntent.PAUSED_BY_USER

    @Volatile
    private var isCurrentlyPlaying: Boolean = false

    private var activeSession: InterruptionSession? = null
    private var lastUserPauseTimestamp: Long = 0L

    private var debounceJob: Job? = null
    private var pollingJob: Job? = null
    private var isRegistered: Boolean = false

    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            super.onPlaybackConfigChanged(configs)
            checkAndScheduleResume()
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                AppLogger.i(TAG, "监听到耳机拔出，取消外部打断恢复等待")
                onExplicitUserPause()
            }
        }
    }

    fun start(scope: CoroutineScope, onResumeRequested: () -> Unit) {
        this.scope = scope
        this.onResumeRequested = onResumeRequested

        scope.launch {
            settingsPreferences.resumeAfterExternalInterruption.collect { enabled ->
                isEnabled = enabled
                if (enabled) {
                    registerListeners()
                } else {
                    cancelWaiting()
                    unregisterListeners()
                }
            }
        }
    }

    fun isAwaitingResumeActive(): Boolean = activeSession != null

    fun onIsPlayingChanged(isPlaying: Boolean) {
        isCurrentlyPlaying = isPlaying
        if (isPlaying) {
            userIntent = UserPlaybackIntent.PLAYING
        }
    }

    fun onPlaybackStarted() {
        userIntent = UserPlaybackIntent.PLAYING
        if (activeSession != null) {
            cancelWaiting()
        }
    }

    fun onExplicitUserPlay() {
        userIntent = UserPlaybackIntent.PLAYING
        if (activeSession != null) {
            cancelWaiting()
        }
    }

    fun onExplicitUserPause() {
        userIntent = UserPlaybackIntent.PAUSED_BY_USER
        lastUserPauseTimestamp = SystemClock.elapsedRealtime()
        if (activeSession != null) {
            AppLogger.i(TAG, "用户主动触发暂停，作废外部打断恢复等待")
            cancelWaiting()
        }
    }

    fun onTrackTransition(newSongId: Long?) {
        if (activeSession != null) {
            AppLogger.i(TAG, "外部打断等待期间发生切歌(songId=$newSongId)，作废恢复等待")
            userIntent = UserPlaybackIntent.PAUSED_BY_USER
            cancelWaiting()
        }
    }

    fun onAudioFocusLoss(playbackState: Int, currentSongId: Long?) {
        if (!isEnabled) return

        if (userIntent != UserPlaybackIntent.PLAYING) {
            AppLogger.i(TAG, "收到音频焦点丢失，但用户意图非播放($userIntent)，忽略")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastUserPauseTimestamp < PAUSE_GRACE_PERIOD_MS) {
            AppLogger.i(TAG, "收到音频焦点丢失，但处于用户主动暂停保护期(${now - lastUserPauseTimestamp}ms)，忽略")
            return
        }

        if (!isCurrentlyPlaying || playbackState != Player.STATE_READY) {
            AppLogger.i(TAG, "收到音频焦点丢失，但丢失前非有效播放出声状态(isPlaying=$isCurrentlyPlaying, state=$playbackState)，忽略")
            return
        }

        AppLogger.i(TAG, "确认音乐由外部音频打断，开始等待外部音频停止 (songId=$currentSongId)")
        activeSession = InterruptionSession(
            songId = currentSongId,
            interruptionTimestamp = now
        )
        debounceJob?.cancel()
        debounceJob = null
        startPollingFallback()
    }

    fun release() {
        cancelWaiting()
        unregisterListeners()
        scope = null
        onResumeRequested = null
    }

    private fun cancelWaiting() {
        activeSession = null
        debounceJob?.cancel()
        debounceJob = null
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun isExternalAudioActive(): Boolean {
        return audioManager.isMusicActive || audioManager.mode != AudioManager.MODE_NORMAL
    }

    private fun checkAndScheduleResume() {
        val session = activeSession ?: return

        if (userIntent != UserPlaybackIntent.PLAYING) {
            cancelWaiting()
            return
        }

        val elapsed = SystemClock.elapsedRealtime() - session.interruptionTimestamp
        if (elapsed > MAX_INTERRUPTION_WINDOW_MS) {
            AppLogger.i(TAG, "外部打断已超过最大等待窗口(${elapsed}ms)，放弃自动恢复")
            cancelWaiting()
            return
        }

        if (isExternalAudioActive()) {
            debounceJob?.cancel()
            debounceJob = null
        } else {
            if (debounceJob?.isActive == true) {
                return
            }
            debounceJob = scope?.launch {
                delay(DEBOUNCE_DELAY_MS)
                val currentSession = activeSession
                if (currentSession != null && userIntent == UserPlaybackIntent.PLAYING && !isExternalAudioActive()) {
                    val currentElapsed = SystemClock.elapsedRealtime() - currentSession.interruptionTimestamp
                    if (currentElapsed <= MAX_INTERRUPTION_WINDOW_MS) {
                        AppLogger.i(TAG, "外部音视频已停止发声，防抖确认通过，触发恢复播放")
                        cancelWaiting()
                        onResumeRequested?.invoke()
                    } else {
                        cancelWaiting()
                    }
                }
            }
        }
    }

    private fun startPollingFallback() {
        pollingJob?.cancel()
        pollingJob = scope?.launch {
            while (activeSession != null) {
                delay(1000L)
                if (activeSession != null) {
                    checkAndScheduleResume()
                }
            }
        }
    }

    private fun registerListeners() {
        if (isRegistered) return
        try {
            audioManager.registerAudioPlaybackCallback(playbackCallback, null)
            val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            ContextCompat.registerReceiver(
                context,
                becomingNoisyReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
            isRegistered = true
        } catch (e: Exception) {
            AppLogger.w(TAG, "注册音频监听失败", e)
        }
    }

    private fun unregisterListeners() {
        if (!isRegistered) return
        try {
            audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        } catch (e: Exception) {
            AppLogger.w(TAG, "注销 AudioPlaybackCallback 失败", e)
        }
        try {
            context.unregisterReceiver(becomingNoisyReceiver)
        } catch (e: Exception) {
            AppLogger.w(TAG, "注销 becomingNoisyReceiver 失败", e)
        }
        isRegistered = false
    }
}
