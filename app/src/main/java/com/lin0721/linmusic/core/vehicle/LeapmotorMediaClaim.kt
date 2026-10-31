package com.lin0721.linmusic.core.vehicle

import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "媒体源"

// 向零跑车机声明"当前媒体源"是自己，方向盘按键才只归自己（否则会与原车音乐双控）。
// 仅在零跑车机（存在 display_0_top_activity）或用户开启车机模式时才工作，手机上不发任何广播。
class LeapmotorMediaClaim(
    private val context: Context,
    private val playback: Lazy<PlaybackController>,
    private val settings: SettingsPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val resolver = context.contentResolver
    private val packageName = context.packageName
    private val isLeapmotorHost = Settings.Global.getString(resolver, LeapmotorProtocol.SETTING_TOP_ACTIVITY) != null

    @Volatile private var active = false
    private var selfOnTop = false
    private var topActivity = ""
    private var sent = 0
    private var skipped = 0

    private val topObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshTop()
            publishNow()
        }
    }

    fun start() {
        scope.launch {
            settings.carMode.collectLatest { forced ->
                if (forced || isLeapmotorHost) runClaim()
            }
        }
    }

    private suspend fun runClaim() = coroutineScope {
        val player = playback.value
        active = true
        resolver.registerContentObserver(
            Settings.Global.getUriFor(LeapmotorProtocol.SETTING_TOP_ACTIVITY), false, topObserver
        )
        try {
            launch {
                combine(player.playWhenReady, player.nowPlaying) { playing, np -> playing to np }
                    .distinctUntilChanged()
                    .collectLatest { publishNow() }
            }
            var tick = 0
            while (isActive) {
                // 心跳里也刷新一次前台状态，防 ContentObserver 漏事件；源会被系统或别的 App 抢走，需持续重推
                refreshTop()
                publishNow()
                if (++tick % 10 == 0) {
                    AppLogger.i(TAG, "广播声明sent=$sent skipped=$skipped 自己在前台=$selfOnTop top=$topActivity")
                }
                delay(LeapmotorProtocol.CLAIM_HEARTBEAT_MS)
            }
        } finally {
            active = false
            resolver.unregisterContentObserver(topObserver)
        }
    }

    private fun refreshTop() {
        topActivity = Settings.Global.getString(resolver, LeapmotorProtocol.SETTING_TOP_ACTIVITY).orEmpty()
        selfOnTop = isSelfOnTop(topActivity, packageName)
    }

    fun publishNow() {
        if (!active) return
        val player = playback.value
        val playing = player.playWhenReady.value
        if (!shouldClaimMediaSource(selfOnTop, playing)) {
            skipped++
            return
        }
        val np = player.nowPlaying.value
        val json = buildMediaInfoJson(np?.title.orEmpty(), np?.artist.orEmpty(), playing)
        val intent = Intent(LeapmotorProtocol.ACTION_MEDIA_INFO)
            .putExtra(LeapmotorProtocol.EXTRA_MEDIA_INFO, json)
            .addFlags(LeapmotorProtocol.FLAG_RECEIVER_INCLUDE_BACKGROUND or LeapmotorProtocol.FLAG_RECEIVER_FOREGROUND)
        try {
            context.sendBroadcast(intent)
            sent++
        } catch (e: Exception) {
            AppLogger.w(TAG, "媒体源声明广播失败", e)
        }
    }
}
