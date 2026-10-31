package com.lin0721.linmusic.feature.artist.domain

import com.lin0721.linmusic.core.model.ArtistAlbum
import com.lin0721.linmusic.core.model.Track

// 歌手专辑分页结果
data class ArtistAlbumPage(
    val albums: List<ArtistAlbum>,
    val hasMore: Boolean
)

// 歌手全部歌曲分页结果
data class ArtistSongsPage(
    val songs: List<Track>,
    val hasMore: Boolean
)
