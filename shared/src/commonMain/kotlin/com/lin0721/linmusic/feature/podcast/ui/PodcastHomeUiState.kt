package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategoryGroup
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio

// 播客首页顶部胶囊的筛选
sealed interface PodcastFilter {
    data object All : PodcastFilter

    data object Subscribed : PodcastFilter

    data class Category(val id: Long) : PodcastFilter
}

// 播客首页状态：每个区块各自 Loading / Success / Error，互不拖累
data class PodcastHomeState(
    val isLoggedIn: Boolean = false,
    val categories: List<PodcastCategory> = emptyList(),
    val filter: PodcastFilter = PodcastFilter.All,
    // 本地进度里未听完的节目，最近优先
    val continueListening: List<PodcastProgressEntry> = emptyList(),
    // 未登录时为空列表，整块不展示
    val subscribed: PodcastSection<List<PodcastRadio>> = PodcastSection.Loading,
    val updatedRadioIds: Set<Long> = emptySet(),
    // 今日优选：推荐节目
    val picks: PodcastSection<List<PodcastProgram>> = PodcastSection.Loading,
    val categoryGroups: PodcastSection<List<PodcastCategoryGroup>> = PodcastSection.Loading,
    val toplistRadios: PodcastSection<List<PodcastRadio>> = PodcastSection.Loading,
    // 选中某个分类时，该分类下的热门电台（首页只取第一页，翻页在分类二级页）
    val categoryRadios: PodcastSection<List<PodcastRadio>> = PodcastSection.Loading,
    // 按 songId 索引的本地进度，用于节目行的进度条与已听完标记
    val progress: Map<Long, PodcastProgressEntry> = emptyMap()
)
