package com.lin0721.linmusic.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import coil3.compose.AsyncImage
import com.lin0721.linmusic.core.ui.theme.BackgroundDark
import com.lin0721.linmusic.core.ui.theme.ContentPrimary
import com.lin0721.linmusic.core.ui.theme.TextGray
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.Text

// 方屏手表的全屏播放页：手机版播放页（封面+信息+歌词+操作区，多屏滚动）在 200dp 见方的屏幕上
// 一屏放不下，这里给一套只保留「封面 + 歌名 + 进度 + 上一首/播放/下一首」的单屏版本
@Composable
fun WatchPlayerScreen(
    currentTrack: MediaItem?,
    isPlaying: Boolean,
    currentPositionProvider: () -> Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null) return

    val title = currentTrack.mediaMetadata.title?.toString() ?: "未知歌名"
    val artist = currentTrack.mediaMetadata.artist?.toString().orEmpty()
    val coverUrl = currentTrack.mediaMetadata.artworkUri?.toString().orEmpty()
    val thumbUrl = remember(coverUrl) {
        if ((coverUrl.startsWith("http://") || coverUrl.startsWith("https://")) && !coverUrl.contains('?')) {
            "$coverUrl?param=240y240"
        } else {
            coverUrl
        }
    }

    // 拖动期间用本地值，松手才真正 seek，避免每一帧都打到播放器
    var draggingValue by remember(currentTrack.mediaId) { mutableStateOf<Float?>(null) }
    val position = currentPositionProvider().coerceAtLeast(0L)
    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownProgress = draggingValue ?: progress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "收起播放页",
                    tint = TextGray,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = title,
                color = ContentPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee()
            )
            Spacer(modifier = Modifier.width(32.dp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.25f))
            ) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        if (artist.isNotBlank()) {
            Text(
                text = artist,
                color = TextGray,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Slider(
            value = shownProgress,
            onValueChange = { draggingValue = it },
            onValueChangeFinished = {
                val target = draggingValue
                draggingValue = null
                if (target != null && duration > 0) onSeek((target * duration).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = ContentPrimary,
                activeTrackColor = ContentPrimary,
                inactiveTrackColor = ContentPrimary.copy(alpha = 0.25f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = ContentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = onTogglePlay, modifier = Modifier.size(52.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = BackgroundDark,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(ContentPrimary)
                        .padding(12.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = ContentPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
