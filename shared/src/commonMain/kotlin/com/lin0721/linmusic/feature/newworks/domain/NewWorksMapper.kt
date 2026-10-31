package com.lin0721.linmusic.feature.newworks.domain

import com.lin0721.linmusic.core.model.Track
import com.lin0721.linmusic.feature.newworks.data.NewWorksReleaseItem

// blockType 非 song/album（未知新类型）时返回 null，交给上层过滤，不硬渲染陌生结构
fun NewWorksReleaseItem.toReleaseDomain(): NewWorksRelease? {
    val blockTitle = info.blockTitle
    val isAlbum = info.blockType == "album"
    if (info.blockType != "song" && !isAlbum) return null

    val firstTrack = info.songLists.firstOrNull()
    val id = if (isAlbum) blockTitle.resourceId else (firstTrack?.id ?: blockTitle.resourceId)
    if (id <= 0) return null

    val cover = blockTitle.resourcePicUrl ?: firstTrack?.al?.picUrl ?: blockTitle.imgUrl
    val artistName = firstTrack?.ar?.joinToString(" / ") { it.name }
        ?.ifBlank { null }
        ?: blockTitle.artistName

    val title = blockTitle.resourceName
    val tracks = if (isAlbum) {
        info.songLists.map { it.toNewWorksTrack(cover) }
    } else {
        listOf(firstTrack?.toNewWorksTrack(cover) ?: NewWorksTrack(id, title, artistName, cover))
    }

    return NewWorksRelease(
        id = id,
        title = title,
        coverUrl = cover,
        artistName = artistName,
        isAlbum = isAlbum,
        trackCount = if (isAlbum) info.albumSongCount else 1,
        publishTime = publishTime.coerceAtLeast(0),
        tracks = tracks
    )
}

private fun Track.toNewWorksTrack(fallbackCover: String): NewWorksTrack = NewWorksTrack(
    id = id,
    title = name,
    artistName = ar.joinToString(" / ") { it.name },
    coverUrl = al.picUrl.ifBlank { fallbackCover }
)
