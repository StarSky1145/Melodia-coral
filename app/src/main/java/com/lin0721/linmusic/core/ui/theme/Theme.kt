package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.lin0721.linmusic.core.ui.interaction.MelodiaPressIndication

// Melodia 应用的全局 Material3 主题配置，配色/圆角/字体随当前界面风格变化
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MelodiaTheme(
    content: @Composable () -> Unit
) {
    val palette = MelodiaStyleState.palette
    val colorScheme = remember(palette) { palette.toColorScheme() }
    val typography = remember(palette.serif) { melodiaTypography(palette.fontFamily) }
    val shapes = remember(palette.cornerScale) { melodiaShapes(palette.cornerScale) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        shapes = shapes
    ) {
        CompositionLocalProvider(
            LocalIndication provides MelodiaPressIndication.Default,
            LocalRippleConfiguration provides null,
            content = content
        )
    }
}
