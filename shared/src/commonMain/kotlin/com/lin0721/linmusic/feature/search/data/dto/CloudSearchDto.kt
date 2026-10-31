package com.lin0721.linmusic.feature.search.data.dto

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.podcast.data.PodcastRadioDto
import kotlinx.serialization.Serializable

@Serializable
data class CloudSearchRequest(
    val s: String,
    val type: Int = 1,      // 1=单曲 10=专辑 100=歌手 1000=歌单 1009=电台，见 SearchType
    val limit: Int = 30,
    val offset: Int = 0
)

@Serializable
data class CloudSearchResponse(
    val code: Int = 0,
    val result: SearchResult? = null
) {
    val isSuccess: Boolean get() = code == 200
}

// 同一响应结构承载五种 type 的结果，每次请求只有对应 type 的字段非空（真机核实）
@Serializable
data class SearchResult(
    val songs: List<Track>? = null,
    val songCount: Int = 0,
    val albums: List<Album>? = null,
    val albumCount: Int = 0,
    val artists: List<Artist>? = null,
    val artistCount: Int = 0,
    val playlists: List<PlaylistDetail>? = null,
    val playlistCount: Int = 0,
    val djRadios: List<PodcastRadioDto>? = null,
    val djRadiosCount: Int = 0
)
