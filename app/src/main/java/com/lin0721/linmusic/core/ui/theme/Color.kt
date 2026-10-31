package com.lin0721.linmusic.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos

// 平滑抗色阶断层的垂直渐变
// 采用余弦缓动曲线采样，首尾斜率平滑过渡；透明渐变保留原色相避免发灰
fun Brush.Companion.smoothVerticalGradient(
    from: Color,
    to: Color,
    startY: Float = 0f,
    endY: Float = Float.POSITIVE_INFINITY,
    fadeEndFraction: Float = 1f,
    steps: Int = 24
): Brush {
    val effectiveTo = if (to == Color.Transparent) from.copy(alpha = 0f) else to
    val effectiveFrom = if (from == Color.Transparent) effectiveTo.copy(alpha = 0f) else from
    val clampedFadeEnd = fadeEndFraction.coerceIn(0f, 1f)

    val stops = Array(if (clampedFadeEnd < 1f) steps + 1 else steps) { i ->
        if (i < steps) {
            val t = i.toFloat() / (steps - 1)
            val factor = ((1.0 - cos(t * PI)) / 2.0).toFloat()
            val color = lerp(effectiveFrom, effectiveTo, factor)
            (t * clampedFadeEnd) to color
        } else {
            1f to effectiveTo
        }
    }

    return verticalGradient(
        colorStops = stops,
        startY = startY,
        endY = endY
    )
}

// HSV 明度按比例偏移，供取色/背景渲染各处按需现算深浅变体，不落地成持久字段。
// 用比例缩放而不是加减法再 coerceIn 夹断：暗色/灰阶封面明度本就 ≤ 0.35，
// 加减法一夹断就是绝对纯黑，色相全丢；比例缩放无论原明度多低都只是"更暗"，不会砸成死黑
fun Color.lighten(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] + (1f - hsv[2]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 封面取色得到的颜色几乎都经过 darken 再拿来铺背景（迷你播放条、歌单头部、播放页等），
// 语义上是"往页面底色方向压"：深色风格按 HSV 明度压暗；浅色风格则混入页面底色，变成同色相的浅色，
// 这样同一套取色代码在浅色风格下自然得到浅色背景
fun Color.darken(amount: Float): Color {
    if (!IsDarkStyle) {
        val mix = (LIGHT_TINT_BASE + LIGHT_TINT_PER_AMOUNT * amount).coerceAtMost(LIGHT_TINT_MAX)
        return lerp(this, MelodiaStyleState.palette.background, mix)
    }
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    hsv[2] = (hsv[2] * (1f - amount)).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 浅色风格下取色背景混入页面底色的比例：amount 越大越接近底色
private const val LIGHT_TINT_BASE = 0.74f
private const val LIGHT_TINT_PER_AMOUNT = 0.36f
private const val LIGHT_TINT_MAX = 0.94f

// 取色原色直接当背景时用：深色风格保持原色，浅色风格换成同色相的浅色
fun Color.styleTone(): Color = if (IsDarkStyle) this else darken(0f)

// 叠在图片、深色遮罩或高饱和色块上的文字/图标，任何风格下都保持白色
val OnImage = Color.White

// 卡片上取色色块的底色：深色风格向黑色压暗，浅色风格换成同色相的浅色
fun Color.spotlightTone(fraction: Float): Color =
    if (IsDarkStyle) lerp(this, Color.Black, fraction) else darken(fraction)

// 主播放按钮：深色风格白底深色图标，浅色风格用强调色底
val PlayButtonContainer: Color get() = if (IsDarkStyle) MelodiaStyleState.palette.content else MelodiaStyleState.palette.accent
val PlayButtonIcon: Color get() = if (IsDarkStyle) MelodiaStyleState.palette.background else MelodiaStyleState.palette.onAccent

// HSV 饱和度按比例往纯色方向推；原色饱和度为 0（灰阶/黑白）时色相无意义，直接跳过，
// 否则会凭空编出一个色相方向的颜色
fun Color.saturate(amount: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsv)
    if (hsv[1] <= 0f) return this
    hsv[1] = (hsv[1] + (1f - hsv[1]) * amount).coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

// 只给有色 base 提饱和度：中性 base 只带 NEUTRAL_BASE_CHROMA 的轻微色调，提饱和度会放大成明显偏色；
// 两个色度档位之间线性过渡，切歌渐变的中间色不跳变
fun Color.saturateIfChromatic(amount: Float): Color {
    val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
    val weight = ((chroma - NEUTRAL_BASE_CHROMA) / (MIN_BASE_CHROMA - NEUTRAL_BASE_CHROMA)).coerceIn(0f, 1f)
    return saturate(amount * weight)
}

// 以下颜色随界面风格变化（见 AppStyle.kt）。沿用原名以免改动全部调用处：
// 名字里的 Dark/Black 是历史遗留，浅色风格下它们是浅色
val BackgroundDark: Color get() = MelodiaStyleState.palette.background
val SurfaceDark: Color get() = MelodiaStyleState.palette.surface
val SurfaceLight: Color get() = MelodiaStyleState.palette.surfaceHigh
val TextGray: Color get() = MelodiaStyleState.palette.contentSecondary

// 主要文字/图标色，替代原先写死的 Color.White
val ContentPrimary: Color get() = MelodiaStyleState.palette.content

// 强调色上的文字/图标
val OnAccent: Color get() = MelodiaStyleState.palette.onAccent

// 当前风格是否为深色
val IsDarkStyle: Boolean get() = MelodiaStyleState.palette.isDark

val NeteaseRed: Color get() = MelodiaStyleState.palette.accent
val GradientStart: Color get() = lerp(MelodiaStyleState.palette.background, MelodiaStyleState.palette.accent, 0.18f)
val BackgroundBlack: Color get() = MelodiaStyleState.palette.backgroundDeep

// 底栏选中项的药丸指示器底色
val NavPillSelected: Color get() = MelodiaStyleState.palette.navPillSelected

// 筛选药丸未选中态底色
val FilterPillInactive: Color get() = MelodiaStyleState.palette.filterPillInactive

// 全局 Toast 底色与文字
val ToastBackground: Color get() = MelodiaStyleState.palette.toastBackground
val ToastContent: Color get() = MelodiaStyleState.palette.toastContent

// 网页登录容器底色，与网页自身背景一致以避免键盘弹出时闪屏
val WebLoginBackground = Color(0xFFF5F5F7)

// 封面取色未命中缓存时的兜底底色，迷你播放器与全屏播放器共用以保证配色一致。
// 它和取色结果一样是"原色"，各处铺背景前都会经过 darken，由 darken 按风格换算深浅，所以这里保持中性深灰
val FallbackBase = Color(0xFF333333)

// 歌单/歌手页在取色完成前的初始底色，同样经过 darken 后才上屏
val CoverPlaceholderDark = Color(0xFF2C2C2C)

// 定时关闭中的剩余时间告警色
val TimerWarningRed = Color(0xFFFF5252)

// 音乐库卡片的装饰渐变：紫罗兰 → 紫 → 粉
val LibraryVioletGradient = listOf(
    Color(0xFF6366F1),
    Color(0xFFA855F7),
    Color(0xFFEC4899)
)

// 音乐库卡片的装饰渐变：蓝 → 翠绿
val LibraryBlueGreenGradient = listOf(
    Color(0xFF3B82F6),
    Color(0xFF10B981)
)

// 已下载标识翠绿色
val DownloadedGreen = Color(0xFF10B981)

// 下载失败标识红色，比品牌红更亮，深色背景上作文字仍可读
val DownloadFailedRed = Color(0xFFFF6B6B)

// SVIP 标识金色
val SvipGold = Color(0xFFD4A017)

// 首页功能入口的策展渐变。心动模式、音乐漫游这类功能本就没有对应封面，
// 用固定色块表达
val EntryHotGradient = listOf(Color(0xFFF08C1A), Color(0xFF9C4A05))
val EntryHeartGradient = listOf(Color(0xFFA33FC4), Color(0xFF4B1268))
val EntryRadarGradient = listOf(Color(0xFF2F7FD6), Color(0xFF123F70))
val EntryRoamingGradient = listOf(Color(0xFF3FAE5C), Color(0xFF175C2C))
