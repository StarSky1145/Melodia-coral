package com.lin0721.linmusic.feature.playlist.domain

import com.lin0721.linmusic.core.model.PlaylistDetail
import com.lin0721.linmusic.core.model.PlaylistTrackId
import com.lin0721.linmusic.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistTrackPagingTest {

    private fun tracks(vararg ids: Long) = ids.map { Track(id = it) }
    private fun detail(loaded: List<Track>, allIds: List<Long>) =
        PlaylistDetail(tracks = loaded, trackIds = allIds.map { PlaylistTrackId(it) })

    @Test
    fun `pending ids are those not yet loaded, in playlist order`() {
        val d = detail(tracks(1, 2, 3), listOf(1, 2, 3, 4, 5))
        assertEquals(listOf(4L, 5L), pendingTrackIds(d))
    }

    @Test
    fun `pending is empty when trackIds is empty (albums, daily recommend)`() {
        assertTrue(pendingTrackIds(detail(tracks(1, 2), emptyList())).isEmpty())
    }

    @Test
    fun `filtered tracks do not cause loaded tail to be refetched`() {
        // 5 首里第 2 首被屏蔽歌手过滤掉：tracks 只有 4 首，但 trackIds 已剔除该 id，不应再有待补全项
        val d = detail(tracks(1, 3, 4, 5), listOf(1, 3, 4, 5))
        assertTrue(pendingTrackIds(d).isEmpty())
    }

    @Test
    fun `withLoadedTracks appends and drops ids that were not returned`() {
        val d = detail(tracks(1, 2), listOf(1, 2, 3, 4, 5))
        // 请求 3,4,5，但 4 被过滤/已下架
        val merged = d.withLoadedTracks(listOf(3, 4, 5), tracks(3, 5))
        assertEquals(listOf(1L, 2L, 3L, 5L), merged.tracks.map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 5L), merged.trackIds.map { it.id })
    }

    @Test
    fun `withLoadedTracks keeps trackIds untouched when everything is returned`() {
        val d = detail(tracks(1), listOf(1, 2, 3))
        val merged = d.withLoadedTracks(listOf(2, 3), tracks(2, 3))
        assertEquals(d.trackIds, merged.trackIds)
        assertEquals(listOf(1L, 2L, 3L), merged.tracks.map { it.id })
    }
}
