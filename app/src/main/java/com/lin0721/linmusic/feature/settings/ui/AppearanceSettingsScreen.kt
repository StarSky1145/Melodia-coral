package com.lin0721.linmusic.feature.settings.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.theme.MelodiaPress
import com.lin0721.linmusic.core.ui.interaction.pressable
import com.lin0721.linmusic.core.ui.theme.AppStyle
import com.lin0721.linmusic.core.ui.theme.MelodiaPalette
import com.lin0721.linmusic.core.ui.theme.MelodiaSpacing
import com.lin0721.linmusic.core.ui.theme.previewPaletteFor
import com.lin0721.linmusic.core.ui.theme.styledCorners

// 界面风格：四张缩略预览卡片，点选后整个界面重建换上新风格
@Composable
fun AppearanceSettingsView(viewModel: SettingsViewModel) {
    val currentId by viewModel.appStyle.collectAsStateWithLifecycle()
    val current = AppStyle.fromId(currentId)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "选择后界面会重新加载一次。柔和取色在 Android 12 及以上跟随壁纸配色",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(start = MelodiaSpacing.xs)
            )
        }
        items(AppStyle.entries, key = { it.id }) { style ->
            StyleOptionCard(
                style = style,
                selected = style == current,
                onClick = { if (style != current) viewModel.updateAppStyle(style.id) }
            )
        }
    }
}

@Composable
private fun StyleOptionCard(style: AppStyle, selected: Boolean, onClick: () -> Unit) {
    val palette = previewPaletteFor(style)
    // 预览卡片按各自风格的圆角画，不跟随当前风格
    val cardShape = RoundedCornerShape(14.dp * palette.cornerScale.coerceAtMost(1.3f))
    Column(modifier = Modifier.fillMaxWidth().pressable(MelodiaPress.Card, onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .clip(cardShape)
                .background(palette.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = cardShape
                )
        ) {
            StyleThumbnail(palette)
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "当前风格",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = style.title,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Text(
            text = if (style == AppStyle.TONAL && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) "系统不支持壁纸取色，使用柔和绿" else style.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// 缩略图：一块推荐卡片、两行歌曲和一个迷你播放条，用该风格自己的配色和圆角绘制
@Composable
private fun StyleThumbnail(palette: MelodiaPalette) {
    val r = palette.cornerScale
    Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp * r))
                .background(palette.content)
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp * r))
                .background(if (palette.isDark) palette.surface else palette.navPillSelected)
                .padding(7.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp * r))
                    .background(palette.accent)
            )
        }
        Spacer(Modifier.height(8.dp))
        repeat(2) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp * r))
                        .background(palette.surfaceHigh)
                )
                Spacer(Modifier.width(6.dp))
                Column {
                    Box(Modifier.width(50.dp).height(5.dp).clip(RoundedCornerShape(3.dp * r)).background(palette.content))
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp * r)).background(palette.contentSecondary))
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp * r))
                .background(palette.surface)
                .padding(5.dp)
        ) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(palette.surfaceHigh))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp * r)).background(palette.contentSecondary))
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier.size(16.dp).clip(CircleShape).background(palette.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.onAccent, modifier = Modifier.size(11.dp))
            }
        }
    }
}
