package com.lin0721.linmusic.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.InfoCardRadius
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.NeteaseRed
import com.lin0721.linmusic.core.ui.theme.TextGray
import com.lin0721.linmusic.core.ui.theme.ContentPrimary

// mini 播放栏上方的通用状态横幅
@Composable
fun MiniStatusBanner(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    progress: Float? = null,
    // 自定义进度区域，提供时替代默认进度条
    progressContent: (@Composable () -> Unit)? = null,
    iconTint: Color = NeteaseRed,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MelodiaSpacing.sm)
            .padding(bottom = MelodiaSpacing.xs)
            .clip(RoundedCornerShape(InfoCardRadius))
            .border(0.5.dp, ContentPrimary.copy(alpha = 0.08f), RoundedCornerShape(InfoCardRadius))
            .background(BackgroundDark)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MelodiaSpacing.md, vertical = MelodiaSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = ContentPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = TextGray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (progressContent != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    progressContent()
                } else if (progress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeteaseRed
                    )
                }
            }
            if (trailingText != null) {
                Spacer(modifier = Modifier.width(MelodiaSpacing.sm))
                Text(
                    text = trailingText,
                    color = TextGray,
                    fontSize = 12.sp
                )
            }
            if (onClose != null) {
                Spacer(modifier = Modifier.width(MelodiaSpacing.xs))
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭",
                    tint = TextGray,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(6.dp)
                )
            }
        }
    }
}
