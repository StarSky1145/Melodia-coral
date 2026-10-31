package com.lin0721.linmusic.feature.newworks.domain

// 发布内的单首曲目，只保留播放队列与卡片摘要需要的字段
data class NewWorksTrack(
    val id: Long,
    val title: String,
    val artistName: String,
    val coverUrl: String
)

data class NewWorksRelease(
    // 单曲：歌曲 id；专辑：albumId，点击跳专辑详情页
    val id: Long,
    val title: String,
    val coverUrl: String,
    val artistName: String,
    val isAlbum: Boolean,
    // 单曲固定为 1，专辑取服务端下发的 albumSongCount
    val trackCount: Int,
    // 毫秒时间戳，0 表示服务端未给
    val publishTime: Long,
    // 专辑为接口内联的全部曲目（可能为空，需按需补拉），单曲为其自身
    val tracks: List<NewWorksTrack> = emptyList()
)

data class NewWorksReleasePage(
    val items: List<NewWorksRelease>,
    val hasMore: Boolean,
    val nextCursor: Long
)
