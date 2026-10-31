package com.lin0721.linmusic.feature.music.data

import com.lin0721.linmusic.core.contentfilter.ContentFilter
import com.lin0721.linmusic.core.network.apiFlow
import com.lin0721.linmusic.feature.music.domain.MusicStyle
import com.lin0721.linmusic.feature.music.domain.StyleAlbumItem
import com.lin0721.linmusic.feature.music.domain.StyleArtistItem
import com.lin0721.linmusic.feature.music.domain.StyleHead
import com.lin0721.linmusic.feature.music.domain.StylePlaylistItem
import com.lin0721.linmusic.feature.music.domain.StylePreference
import com.lin0721.linmusic.feature.music.domain.StyleSongPage
import com.lin0721.linmusic.feature.music.domain.StyleSort
import com.lin0721.linmusic.feature.music.domain.toMusicStyles
import com.lin0721.linmusic.feature.music.domain.toStyleAlbumItems
import com.lin0721.linmusic.feature.music.domain.toStyleArtistItems
import com.lin0721.linmusic.feature.music.domain.toStyleHead
import com.lin0721.linmusic.feature.music.domain.toStylePlaylistItems
import com.lin0721.linmusic.feature.music.domain.toStylePreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

class MusicRepositoryImpl(
    private val apiService: MusicApi,
    private val contentFilter: ContentFilter
) : MusicRepository {

    // 列表 140KB 起步且几乎不变，浏览页与详情页共用这一份
    @Volatile
    private var cachedStyles: List<MusicStyle>? = null

    override fun getStyleList(): Flow<Result<List<MusicStyle>>> = flow {
        cachedStyles?.let {
            emit(Result.success(it))
            return@flow
        }
        emitAll(
            apiFlow(
                request = { apiService.getStyleList() },
                isSuccess = { it.isSuccess },
                code = { it.code },
                transform = { it.data.toMusicStyles() }
            ).onEach { result -> result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { cachedStyles = it } }
        )
    }

    override fun getStylePreferences(): Flow<Result<List<StylePreference>>> = apiFlow(
        request = { apiService.getStylePreference() },
        isSuccess = { it.isSuccess },
        code = { it.code },
        transform = { it.data?.toStylePreferences().orEmpty() }
    )

    override fun getStyleHead(tagId: Long): Flow<Result<StyleHead>> = apiFlow(
        request = { apiService.getStyleHead(StyleHeadRequest(tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.toStyleHead() }
    )

    override fun getStylePlaylists(tagId: Long): Flow<Result<List<StylePlaylistItem>>> = apiFlow(
        request = { apiService.getStylePlaylists(StyleContentRequest(tagId = tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.playlist.toStylePlaylistItems() }
    )

    override fun getStyleSongs(tagId: Long, cursor: Int, sort: StyleSort): Flow<Result<StyleSongPage>> = apiFlow(
        request = {
            apiService.getStyleSongs(
                StyleContentRequest(tagId = tagId, cursor = cursor, size = STYLE_SONG_PAGE_SIZE, sort = sort.value)
            )
        },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { response ->
            val data = response.data!!
            StyleSongPage(
                songs = contentFilter.filterBlockedArtists(data.songs) { song -> song.ar.map { it.id } },
                nextCursor = cursor + STYLE_SONG_PAGE_SIZE,
                // 没带 page 时按是否满页判断
                hasMore = data.page?.more ?: (data.songs.size >= STYLE_SONG_PAGE_SIZE)
            )
        }
    )

    override fun getStyleAlbums(tagId: Long, sort: StyleSort): Flow<Result<List<StyleAlbumItem>>> = apiFlow(
        request = { apiService.getStyleAlbums(StyleContentRequest(tagId = tagId, sort = sort.value)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.albums.toStyleAlbumItems() }
    )

    override fun getStyleArtists(tagId: Long): Flow<Result<List<StyleArtistItem>>> = apiFlow(
        request = { apiService.getStyleArtists(StyleContentRequest(tagId = tagId)) },
        isSuccess = { it.isSuccess && it.data != null },
        code = { it.code },
        transform = { it.data!!.artists.toStyleArtistItems() }
    )
}
