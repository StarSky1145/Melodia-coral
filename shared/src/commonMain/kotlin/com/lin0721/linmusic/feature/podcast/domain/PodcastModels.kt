package com.lin0721.linmusic.feature.podcast.domain

// 电台分类
data class PodcastCategory(
    val id: Long,
    val name: String
)

// 电台：一个系列，本身不可播放，须进详情页听其中的节目
data class PodcastRadio(
    val id: Long,
    val name: String,
    val picUrl: String,
    val programCount: Int,
    val subCount: Long,
    val djName: String,
    val recommendText: String = "",
    // 订阅列表与分类热门接口带最新一期信息，其余接口缺省
    val lastProgramName: String = "",
    val lastProgramCreateTimeMs: Long = 0
)

// 分类分组推荐：一个分类及其推荐电台
data class PodcastCategoryGroup(
    val categoryId: Long,
    val categoryName: String,
    val radios: List<PodcastRadio>
)

// 分页结果，hasMore 为假表示没有下一页
data class PodcastPage<T>(
    val items: List<T>,
    val hasMore: Boolean
)

// 节目：一期，可直接播放
data class PodcastProgram(
    val id: Long,
    // 播放用的歌曲 id，与节目自身 id 不是一回事
    val songId: Long,
    val name: String,
    val coverUrl: String,
    val durationMs: Long,
    val createTimeMs: Long,
    val listenerCount: Long,
    val serialNum: Int,
    val radioId: Long,
    val radioName: String,
    val djName: String,
    val description: String = ""
)

// 电台详情页头部
data class PodcastRadioDetail(
    val id: Long,
    val name: String,
    val picUrl: String,
    val desc: String,
    val category: String,
    val programCount: Int,
    val subCount: Long,
    val djName: String,
    val djAvatarUrl: String,
    val subscribed: Boolean
)
