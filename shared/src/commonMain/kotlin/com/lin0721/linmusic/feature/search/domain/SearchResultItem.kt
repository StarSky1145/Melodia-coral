package com.lin0721.linmusic.feature.search.domain

import com.lin0721.linmusic.core.model.Album
import com.lin0721.linmusic.core.model.Artist
import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio

// 分类型搜索结果条目，按 SearchType 承载对应的共享领域模型，不新建重复模型
sealed interface SearchResultItem {
    val id: Long
    val stableKey: String

    data class SongItem(val track: Track) : SearchResultItem {
        override val id: Long get() = track.id
        override val stableKey: String get() = "song_${track.id}"
    }

    data class AlbumItem(val album: Album) : SearchResultItem {
        override val id: Long get() = album.id
        override val stableKey: String get() = "album_${album.id}"
    }

    data class ArtistItem(val artist: Artist) : SearchResultItem {
        override val id: Long get() = artist.id
        override val stableKey: String get() = "artist_${artist.id}"
    }

    data class PlaylistItem(val playlist: PlaylistDetail) : SearchResultItem {
        override val id: Long get() = playlist.id
        override val stableKey: String get() = "playlist_${playlist.id}"
    }

    data class RadioItem(val radio: PodcastRadio) : SearchResultItem {
        override val id: Long get() = radio.id
        override val stableKey: String get() = "radio_${radio.id}"
    }

    data class ProgramItem(val program: PodcastProgram) : SearchResultItem {
        override val id: Long get() = program.id
        override val stableKey: String get() = "program_${program.id}"
    }
}

// 单个 SearchType 的分页搜索结果
data class SearchPageResult(
    val items: List<SearchResultItem>,
    val totalCount: Int,
    val hasMore: Boolean,
    // 本页从接口实际拉取的原始条数（内容过滤前），分页 offset 必须按此推进，
    // 若按过滤后的 items.size 推进，屏蔽歌手造成的缺口会让下一页请求重复拉取已消费的原始数据
    val rawFetchedCount: Int
)
