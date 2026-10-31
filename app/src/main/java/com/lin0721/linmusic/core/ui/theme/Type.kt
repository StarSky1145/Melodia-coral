package com.lin0721.linmusic.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)

// 全应用的文字几乎都是 Text(fontSize = …) 直接指定字号，字体来自 LocalTextStyle（即 bodyLarge），
// 所以换字体要把每一档样式都替换掉
fun melodiaTypography(fontFamily: FontFamily): Typography {
    if (fontFamily == FontFamily.Default) return BaseTypography
    fun TextStyle.withFamily() = copy(fontFamily = fontFamily)
    return with(BaseTypography) {
        Typography(
            displayLarge = displayLarge.withFamily(),
            displayMedium = displayMedium.withFamily(),
            displaySmall = displaySmall.withFamily(),
            headlineLarge = headlineLarge.withFamily(),
            headlineMedium = headlineMedium.withFamily(),
            headlineSmall = headlineSmall.withFamily(),
            titleLarge = titleLarge.withFamily(),
            titleMedium = titleMedium.withFamily(),
            titleSmall = titleSmall.withFamily(),
            bodyLarge = bodyLarge.withFamily(),
            bodyMedium = bodyMedium.withFamily(),
            bodySmall = bodySmall.withFamily(),
            labelLarge = labelLarge.withFamily(),
            labelMedium = labelMedium.withFamily(),
            labelSmall = labelSmall.withFamily(),
        )
    }
}
