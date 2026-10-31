package com.lin0721.linmusic.core.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

// 方屏手表适配：屏幕可用宽度极小（典型 466px / 2.0x ≈ 233dp，部分 192dp）时，
// 手机版排布里的底栏文字、迷你条按钮、页面横向留白都会挤在一起，单独走一套更紧凑的排布。
// 判定用宽高双上限，避免把 320dp 的小屏手机也误判成手表
private const val WATCH_MAX_WIDTH_DP = 300
private const val WATCH_MAX_HEIGHT_DP = 400

// 手表上整体界面缩到 80%：一套手机排布要在 200dp 见方的屏幕上放下，
// 靠缩密度比给每个页面单独改尺寸更可靠，且与设置里的「界面缩放」共用同一条链路
const val WATCH_UI_SCALE_PERCENT = 80

fun Configuration.isWatchSize(): Boolean =
    screenWidthDp <= WATCH_MAX_WIDTH_DP && screenHeightDp <= WATCH_MAX_HEIGHT_DP

@Composable
fun rememberMelodiaWatchMode(): Boolean {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        configuration.isWatchSize()
    }
}

// 只让真正读取它的组件重组，避免整棵页面树跟着换一套
val LocalMelodiaWatchMode = compositionLocalOf { false }
