package com.lin0721.linmusic.feature.newworks.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
private const val SUMMARY_NAME_LIMIT = 6

private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// 发布时间的相对说法：不足 7 天用「刚刚/N 分钟前/N 小时前/N 天前」，更早用日期；无发布时间返回空串
fun formatRelativeTime(publishTime: Long, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    if (publishTime <= 0) return ""
    val diff = nowMillis - publishTime
    return when {
        diff < MINUTE_MS -> "刚刚"
        diff < HOUR_MS -> "${diff / MINUTE_MS} 分钟前"
        diff < DAY_MS -> "${diff / HOUR_MS} 小时前"
        diff < 7 * DAY_MS -> "${diff / DAY_MS} 天前"
        else -> Instant.ofEpochMilli(publishTime).atZone(zone).toLocalDate().format(dateFormatter)
    }
}

val NewWorksRelease.typeLabel: String get() = if (isAlbum) "专辑" else "单曲"

// 「4 首歌曲 • 曲名 • 曲名」，曲目为空时只剩数量
fun NewWorksRelease.trackSummary(): String {
    val names = tracks.take(SUMMARY_NAME_LIMIT).map { it.title }.filter { it.isNotBlank() }
    return (listOf("$trackCount 首歌曲") + names).joinToString(" • ")
}
