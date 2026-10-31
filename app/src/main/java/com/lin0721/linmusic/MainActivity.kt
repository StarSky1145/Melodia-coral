package com.lin0721.linmusic

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import com.lin0721.linmusic.feature.recognition.service.ACTION_OPEN_RECOGNITION_RESULT
import com.lin0721.linmusic.feature.recognition.service.PlaybackRecognitionState
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lin0721.linmusic.core.link.SharedLinkRequests
import com.lin0721.linmusic.core.player.FloatingLyricService
import com.lin0721.linmusic.core.player.floatinglyric.ACTION_OPEN_LYRIC_SETTINGS
import com.lin0721.linmusic.core.player.floatinglyric.FloatingLyricRequests
import com.lin0721.linmusic.core.preferences.SettingsPreferences
import com.lin0721.linmusic.core.preferences.UiScale
import com.lin0721.linmusic.core.ui.theme.AppStyle
import com.lin0721.linmusic.core.ui.theme.LocalMelodiaWatchMode
import com.lin0721.linmusic.core.ui.theme.LocalUiMotion
import com.lin0721.linmusic.core.ui.theme.MelodiaStyleState
import com.lin0721.linmusic.core.ui.theme.MelodiaTheme
import com.lin0721.linmusic.core.ui.theme.UiMotion
import com.lin0721.linmusic.core.ui.theme.WATCH_UI_SCALE_PERCENT
import com.lin0721.linmusic.core.ui.theme.isWatchSize
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaWatchMode
import com.lin0721.linmusic.core.ui.theme.scaledBy
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {

    private val settingsPreferences: SettingsPreferences by inject()
    private val playbackRecognitionState: PlaybackRecognitionState by inject()
    private val floatingLyricRequests: FloatingLyricRequests by inject()
    private val sharedLinkRequests: SharedLinkRequests by inject()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // 本次创建时已应用的缩放百分比；偏好里的值与它不一致就重建界面
    private var appliedUiScalePercent = UiScale.DEFAULT_PERCENT

    // 偏好里存着的缩放值。手表会自动叠一层默认缩放，重建判定必须比"用户设定值"，
    // 否则 100 的设定值和 80 的实际值会来回触发 recreate
    private var storedUiScalePercent = UiScale.DEFAULT_PERCENT

    // 本次创建时已应用的界面风格；偏好里的值与它不一致就重建界面
    private var appliedStyle = AppStyle.DEFAULT

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        // Activity 创建时 Koin 已由 Application 启动；读取失败按不缩放处理
        storedUiScalePercent = runCatching {
            runBlocking { GlobalContext.get().get<SettingsPreferences>().uiScalePercent.first() }
        }.getOrDefault(UiScale.DEFAULT_PERCENT)
        appliedUiScalePercent = storedUiScalePercent
        // 方屏手表：用户没动过缩放时套用 80%，手机排布才塞得进 200dp 见方
        if (appliedUiScalePercent == UiScale.DEFAULT_PERCENT &&
            newBase.resources.configuration.isWatchSize()
        ) {
            appliedUiScalePercent = WATCH_UI_SCALE_PERCENT
        }
        val factor = UiScale.toFactor(appliedUiScalePercent)
        if (factor != 1f) applyOverrideConfiguration(newBase.resources.configuration.scaledBy(factor))
    }

    // 覆盖配置里的 dp 尺寸是创建时的快照，旋转等配置变化后会过期，重建以按新配置重新换算
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (storedUiScalePercent != UiScale.DEFAULT_PERCENT) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 开启宽色域，提升暗部与渐变渲染位深
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        }
        // 界面风格决定配色深浅，系统栏图标颜色随之切换，不跟随系统亮暗色
        appliedStyle = runCatching {
            AppStyle.fromId(runBlocking { settingsPreferences.appStyle.first() })
        }.getOrDefault(AppStyle.DEFAULT)
        MelodiaStyleState.apply(appliedStyle, this)
        val palette = MelodiaStyleState.palette
        window.decorView.setBackgroundColor(palette.background.toArgb())
        val barStyle = if (palette.isDark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
        requestNotificationPermissionIfNeeded()
        handleLaunchIntent(intent)

        // 监听悬浮歌词开关
        lifecycleScope.launch {
            settingsPreferences.showDesktopLrc.collectLatest { enabled ->
                if (enabled) {
                    startFloatingLyricOrRequestPermission()
                } else {
                    stopService(Intent(this@MainActivity, FloatingLyricService::class.java))
                }
            }
        }

        // 切换界面风格后重建界面，所有页面统一换上新配色
        lifecycleScope.launch {
            settingsPreferences.appStyle.collect { id ->
                if (AppStyle.fromId(id) != appliedStyle) recreate()
            }
        }

        // 设置页松手后写入新缩放，重建界面让它落到 Activity 配置上
        lifecycleScope.launch {
            settingsPreferences.uiScalePercent.collect { percent ->
                if (percent != storedUiScalePercent) recreate()
            }
        }

        setContent {
            val uiAnimationsEnabled by settingsPreferences.uiAnimationsEnabled.collectAsState(initial = true)
            val uiMotion = remember { UiMotion() }
            LaunchedEffect(uiAnimationsEnabled) { uiMotion.enabled = uiAnimationsEnabled }
            val watchMode = rememberMelodiaWatchMode()
            CompositionLocalProvider(
                LocalUiMotion provides uiMotion,
                LocalMelodiaWatchMode provides watchMode
            ) {
                MelodiaTheme {
                    MelodiaApp()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    // 识曲结果通知、悬浮歌词设置按钮、其他 App 分享的网易云链接：交给界面层打开对应页面
    private fun handleLaunchIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_RECOGNITION_RESULT -> playbackRecognitionState.requestOpenResult()
            ACTION_OPEN_LYRIC_SETTINGS -> floatingLyricRequests.requestOpenSettings()
            Intent.ACTION_VIEW -> submitSharedLink(intent.dataString)
            Intent.ACTION_SEND -> submitSharedLink(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }

    private fun submitSharedLink(text: String?) {
        if (!sharedLinkRequests.submit(text)) {
            android.widget.Toast.makeText(this, "没有识别到网易云音乐链接", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val enabled = settingsPreferences.showDesktopLrc.first()
            if (enabled && android.provider.Settings.canDrawOverlays(this@MainActivity)) {
                startService(Intent(this@MainActivity, FloatingLyricService::class.java))
            }
        }
    }

    // Android 13+ 需要运行时授权才能显示系统通知
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 有悬浮窗权限则直接启动桌面歌词，否则需要前往系统设置授权
    private fun startFloatingLyricOrRequestPermission() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingLyricService::class.java))
            return
        }
        android.widget.Toast.makeText(
            this,
            "请开启悬浮窗权限以显示桌面歌词",
            android.widget.Toast.LENGTH_LONG
        ).show()
        startActivity(
            Intent(
                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
        )
    }
}
