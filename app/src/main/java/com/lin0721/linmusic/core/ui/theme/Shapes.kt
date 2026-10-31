package com.lin0721.linmusic.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 当前风格的圆角缩放：直角杂志为 0，柔和取色放大
private val cornerScale: Float get() = MelodiaStyleState.palette.cornerScale

// 按当前风格缩放的圆角半径
fun styledRadius(radius: Dp): Dp = radius * cornerScale

// 按当前风格缩放的圆角形状，替代写死半径的 RoundedCornerShape(x.dp)
fun styledCorners(radius: Dp): Shape = RoundedCornerShape(styledRadius(radius))

// MaterialTheme 的圆角梯度
fun melodiaShapes(scale: Float) = Shapes(
    extraSmall = RoundedCornerShape(4.dp * scale),
    small = RoundedCornerShape(8.dp * scale),
    medium = RoundedCornerShape(16.dp * scale),
    large = RoundedCornerShape(24.dp * scale),
    extraLarge = RoundedCornerShape(32.dp * scale)
)

val MelodiaShapes: Shapes get() = melodiaShapes(cornerScale)

// BottomSheet 顶部圆角
val BottomSheetShape: Shape get() = RoundedCornerShape(topStart = styledRadius(24.dp), topEnd = styledRadius(24.dp))

// BottomSheet 拖拽把手的胶囊圆角
val DragHandleShape: Shape = RoundedCornerShape(2.dp)

// 封面图/缩略图圆角
val RadiusCompact: Dp get() = styledRadius(6.dp)

// 播放详情页信息卡片圆角（歌词/评论/歌曲详情/类似艺人/关于艺人/艺人专辑）
val InfoCardRadius: Dp get() = styledRadius(16.dp)

// 胶囊型按钮/Chip 圆角
val PillRadius: Dp get() = styledRadius(20.dp)
val PillRadiusLarge: Dp get() = styledRadius(25.dp)
