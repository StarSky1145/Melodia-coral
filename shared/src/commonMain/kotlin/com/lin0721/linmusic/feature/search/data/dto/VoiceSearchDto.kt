package com.lin0721.linmusic.feature.search.data.dto

import com.lin0721.linmusic.feature.podcast.data.PodcastProgramDto
import kotlinx.serialization.Serializable

@Serializable
data class VoiceSearchRequest(
    val keyword: String,
    val scene: String = "normal",
    val limit: Int = 30,
    val offset: Int = 0
)

@Serializable
data class VoiceSearchResponse(
    val code: Int = 0,
    val data: VoiceSearchData? = null
) {
    val isSuccess: Boolean get() = code == 200
}

@Serializable
data class VoiceSearchData(
    val resources: List<VoiceSearchResource> = emptyList(),
    val totalCount: Int = 0,
    val hasMore: Boolean = false
)

// 每条结果的 baseInfo 就是节目结构，其余字段是前端展示用的装饰信息
@Serializable
data class VoiceSearchResource(
    val baseInfo: PodcastProgramDto? = null
)
