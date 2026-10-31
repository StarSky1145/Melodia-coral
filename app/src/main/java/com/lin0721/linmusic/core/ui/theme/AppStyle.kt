package com.lin0721.linmusic.core.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

// 界面风格。id 落盘到设置里，改名前要考虑旧值兼容
enum class AppStyle(val id: String, val title: String, val subtitle: String) {
    CORAL("coral", "留白珊瑚", "浅色、大留白、一个强调色"),
    IMMERSIVE("immersive", "深色沉浸", "黑底，播放页铺满封面色"),
    MAGAZINE("magazine", "直角杂志", "纸张底色、直角、衬线字体"),
    TONAL("tonal", "柔和取色", "跟随壁纸取色，大圆角");

    companion object {
        val DEFAULT = CORAL
        fun fromId(id: String?): AppStyle = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

// 一套风格的全部视觉参数。旧代码里写死的深色常量（BackgroundDark、Color.White 等）都改成从这里取值
@Immutable
data class MelodiaPalette(
    val isDark: Boolean,
    // 页面底色
    val background: Color,
    // 比页面底色再深/再沉一档，平板停靠面板等处使用
    val backgroundDeep: Color,
    // 卡片、弹窗、底部弹层
    val surface: Color,
    // 骨架屏、输入框、占位块
    val surfaceHigh: Color,
    // 主要文字与图标
    val content: Color,
    // 次要文字
    val contentSecondary: Color,
    // 品牌强调色
    val accent: Color,
    // 强调色上的文字
    val onAccent: Color,
    // 底栏选中项药丸
    val navPillSelected: Color,
    // 筛选药丸未选中
    val filterPillInactive: Color,
    // 全局 Toast 底色与文字
    val toastBackground: Color,
    val toastContent: Color,
    // 圆角缩放：直角风格为 0，柔和风格放大
    val cornerScale: Float,
    // 是否整体使用衬线字体
    val serif: Boolean,
) {
    val fontFamily: FontFamily get() = if (serif) FontFamily.Serif else FontFamily.Default
}

private val CoralPalette = MelodiaPalette(
    isDark = false,
    background = Color(0xFFFFFFFF),
    backgroundDeep = Color(0xFFF6F5F2),
    surface = Color(0xFFF4F2EE),
    surfaceHigh = Color(0xFFE9E6DF),
    content = Color(0xFF1F1E1C),
    contentSecondary = Color(0xFF7A7872),
    accent = Color(0xFFD85A30),
    onAccent = Color.White,
    navPillSelected = Color(0xFFFAECE7),
    filterPillInactive = Color(0xFFF1EFE8),
    toastBackground = Color(0xFF2C2C2A),
    toastContent = Color(0xFFF4F2EC),
    cornerScale = 1f,
    serif = false,
)

private val ImmersivePalette = MelodiaPalette(
    isDark = true,
    background = Color(0xFF141413),
    backgroundDeep = Color(0xFF0B0B0A),
    surface = Color(0xFF262624),
    surfaceHigh = Color(0xFF3A3936),
    content = Color(0xFFF4F2EC),
    contentSecondary = Color(0xFFA8A69F),
    accent = Color(0xFFE8693E),
    onAccent = Color.White,
    navPillSelected = Color(0xFF3A2A24),
    filterPillInactive = Color(0xFF2E2C2A),
    toastBackground = Color(0xFF33322F),
    toastContent = Color(0xFFF4F2EC),
    cornerScale = 1f,
    serif = false,
)

private val MagazinePalette = MelodiaPalette(
    isDark = false,
    background = Color(0xFFF3F1EA),
    backgroundDeep = Color(0xFFEAE7DE),
    surface = Color(0xFFFBFAF6),
    surfaceHigh = Color(0xFFE3DFD4),
    content = Color(0xFF1C1B19),
    contentSecondary = Color(0xFF6F6D66),
    accent = Color(0xFFD85A30),
    onAccent = Color.White,
    navPillSelected = Color(0xFFE3DFD4),
    filterPillInactive = Color(0xFFE8E4DA),
    toastBackground = Color(0xFF1C1B19),
    toastContent = Color(0xFFF3F1EA),
    cornerScale = 0f,
    serif = true,
)

// 系统不支持壁纸取色（Android 12 以下）时的柔和绿
private val TonalFallbackPalette = MelodiaPalette(
    isDark = false,
    background = Color(0xFFF4F8EE),
    backgroundDeep = Color(0xFFEAF3DE),
    surface = Color(0xFFE6EFD9),
    surfaceHigh = Color(0xFFD6E5C1),
    content = Color(0xFF1B2610),
    contentSecondary = Color(0xFF4F5E3E),
    accent = Color(0xFF3B6D11),
    onAccent = Color.White,
    navPillSelected = Color(0xFFC0DD97),
    filterPillInactive = Color(0xFFE0EBD0),
    toastBackground = Color(0xFF27500A),
    toastContent = Color(0xFFEAF3DE),
    cornerScale = 1.6f,
    serif = false,
)

// 柔和取色：Android 12+ 用系统壁纸色生成的动态配色
private fun tonalPalette(context: Context): MelodiaPalette {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return TonalFallbackPalette
    val scheme = dynamicLightColorScheme(context)
    return TonalFallbackPalette.copy(
        background = scheme.surface,
        backgroundDeep = scheme.surfaceContainer,
        surface = scheme.surfaceContainerHigh,
        surfaceHigh = scheme.surfaceContainerHighest,
        content = scheme.onSurface,
        contentSecondary = scheme.onSurfaceVariant,
        accent = scheme.primary,
        onAccent = scheme.onPrimary,
        navPillSelected = scheme.secondaryContainer,
        filterPillInactive = scheme.surfaceContainerHigh,
        toastBackground = scheme.inverseSurface,
        toastContent = scheme.inverseOnSurface,
    )
}

fun paletteFor(style: AppStyle, context: Context): MelodiaPalette = when (style) {
    AppStyle.CORAL -> CoralPalette
    AppStyle.IMMERSIVE -> ImmersivePalette
    AppStyle.MAGAZINE -> MagazinePalette
    AppStyle.TONAL -> tonalPalette(context)
}

// 设置页风格预览卡片用的静态色板（不读取系统壁纸，保证预览稳定）
fun previewPaletteFor(style: AppStyle): MelodiaPalette = when (style) {
    AppStyle.CORAL -> CoralPalette
    AppStyle.IMMERSIVE -> ImmersivePalette
    AppStyle.MAGAZINE -> MagazinePalette
    AppStyle.TONAL -> TonalFallbackPalette
}

// 当前风格。由 MainActivity 在创建界面前写入；切换风格时 Activity 会重建，
// 所以界面生命周期内它基本是常量。用快照状态保存，组合与绘制阶段读取都会被追踪
object MelodiaStyleState {
    var style by mutableStateOf(AppStyle.DEFAULT)
        private set
    var palette by mutableStateOf(CoralPalette)
        private set

    fun apply(style: AppStyle, context: Context) {
        this.style = style
        palette = paletteFor(style, context)
    }
}

// 当前风格的 Material 配色
internal fun MelodiaPalette.toColorScheme(): ColorScheme {
    val outline = lerp(background, content, 0.28f)
    val outlineVariant = lerp(background, content, 0.12f)
    val accentContainer = lerp(background, accent, if (isDark) 0.28f else 0.14f)
    return if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = content,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = navPillSelected,
            onSecondaryContainer = content,
            tertiary = accent,
            onTertiary = onAccent,
            background = background,
            onBackground = content,
            surface = surface,
            onSurface = content,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = contentSecondary,
            surfaceTint = accent,
            inverseSurface = content,
            inverseOnSurface = background,
            inversePrimary = accent,
            error = Color(0xFFFF6B6B),
            onError = Color.White,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = surfaceHigh,
            surfaceDim = backgroundDeep,
            surfaceContainerLowest = backgroundDeep,
            surfaceContainerLow = background,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceHigh,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = content,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = navPillSelected,
            onSecondaryContainer = content,
            tertiary = accent,
            onTertiary = onAccent,
            background = background,
            onBackground = content,
            surface = surface,
            onSurface = content,
            surfaceVariant = surfaceHigh,
            onSurfaceVariant = contentSecondary,
            surfaceTint = accent,
            inverseSurface = toastBackground,
            inverseOnSurface = toastContent,
            inversePrimary = accent,
            error = Color(0xFFC62828),
            onError = Color.White,
            outline = outline,
            outlineVariant = outlineVariant,
            scrim = Color.Black,
            surfaceBright = background,
            surfaceDim = backgroundDeep,
            surfaceContainerLowest = background,
            surfaceContainerLow = background,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceHigh,
        )
    }
}
