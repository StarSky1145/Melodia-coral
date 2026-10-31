package com.lin0721.linmusic.core.vehicle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "方控"

// 零跑方向盘按键 → 本地播放器。必须在 Application.onCreate 里进程级动态注册：
// 挂在 Activity/Service 上会导致从别的入口进入时方控失效。
// 接收侧刻意不加"前台门"，否则 App 在后台放歌时方控不响应。
class LeapmotorSteeringControl(
    private val context: Context,
    private val playback: Lazy<PlaybackController>,
    // 按键后立即重推一次媒体源声明，抢回方控归属
    private val onKeyReceived: () -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val debouncer = SteeringKeyDebouncer()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val code = intent?.readSteeringCode() ?: return
            val key = SteeringKey.fromCode(code)
            AppLogger.i(TAG, "方控广播: action=$code")
            if (key == null) return
            if (!debouncer.accept(key, SystemClock.elapsedRealtime())) return
            onKeyReceived()
            dispatch(key)
        }
    }

    fun register() {
        // 自定义 action 来自别的进程，targetSdk>=34 必须显式 EXPORTED
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(LeapmotorProtocol.ACTION_STEERING),
            ContextCompat.RECEIVER_EXPORTED
        )
        AppLogger.i(TAG, "方控接收器已注册（${LeapmotorProtocol.ACTION_STEERING}）")
    }

    private fun dispatch(key: SteeringKey) {
        scope.launch {
            try {
                // 进程可能由通知或别的入口拉起，此时 MediaController 未必已连接
                playback.value.initController()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "方控：初始化播放控制器失败", e)
            }
            val player = playback.value
            when (key) {
                // 用 skipToPrevious 而非 playPrevious：后者播放超过 3 秒只会回到本曲开头
                SteeringKey.PREVIOUS -> player.skipToPrevious()
                SteeringKey.NEXT -> player.playNext()
                SteeringKey.PLAY_PAUSE -> player.togglePlayPause()
            }
            AppLogger.i(TAG, "方控 → $key")
        }
    }

    // 不同固件下 extras 可能是 Int 或 String
    private fun Intent.readSteeringCode(): Int? {
        val name = LeapmotorProtocol.EXTRA_STEERING_ACTION
        val extras = extras ?: return null
        return when (val raw = extras.get(name)) {
            is Int -> raw
            is String -> raw.trim().toIntOrNull()
            is Number -> raw.toInt()
            else -> null
        }
    }
}
