package com.lin0721.linmusic.desktop.ui.palette

import androidx.compose.ui.graphics.Color

// HSV 明度/饱和度按比例偏移，移植自移动端 Color.kt。
// 用比例缩放而不是加减法再夹断：暗色/灰阶封面明度本就很低，加减法一夹断就是纯黑、色相全丢
private fun Color.toHsb(): FloatArray {
    val hsb = FloatArray(3)
    java.awt.Color.RGBtoHSB((red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt(), hsb)
    return hsb
}

private fun hsbToColor(hsb: FloatArray): Color = Color(java.awt.Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]) or (0xFF shl 24))

fun Color.lighten(amount: Float): Color {
    val hsb = toHsb()
    hsb[2] = (hsb[2] + (1f - hsb[2]) * amount).coerceIn(0f, 1f)
    return hsbToColor(hsb)
}

fun Color.darken(amount: Float): Color {
    val hsb = toHsb()
    hsb[2] = (hsb[2] * (1f - amount)).coerceIn(0f, 1f)
    return hsbToColor(hsb)
}

// 原色饱和度为 0（灰阶）时色相无意义，直接跳过，否则会凭空编出一个色相方向的颜色
fun Color.saturate(amount: Float): Color {
    val hsb = toHsb()
    if (hsb[1] <= 0f) return this
    hsb[1] = (hsb[1] + (1f - hsb[1]) * amount).coerceIn(0f, 1f)
    return hsbToColor(hsb)
}

// 只给有色 base 提饱和度：中性 base 只带 NEUTRAL_BASE_CHROMA 的轻微色调，提饱和度会放大成明显偏色；
// 两个色度档位之间线性过渡，切歌渐变的中间色不跳变
fun Color.saturateIfChromatic(amount: Float): Color {
    val chroma = maxOf(red, green, blue) - minOf(red, green, blue)
    val weight = ((chroma - NEUTRAL_BASE_CHROMA) / (MIN_BASE_CHROMA - NEUTRAL_BASE_CHROMA)).coerceIn(0f, 1f)
    return saturate(amount * weight)
}
