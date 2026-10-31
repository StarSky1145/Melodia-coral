package com.lin0721.linmusic.feature.localmusic.data

import android.net.Uri
import com.lin0721.linmusic.core.localmusic.LocalMusicApi
import com.lin0721.linmusic.feature.localmusic.data.lyrics.LocalLyricsReader

class LocalMusicApiImpl(
    private val coverArtCache: LocalCoverArtCache,
    private val lyricsReader: LocalLyricsReader
) : LocalMusicApi {

    override suspend fun coverUriFor(sourceUri: Uri): Uri? = coverArtCache.coverUriFor(sourceUri)

    override suspend fun readLyrics(sourceUri: String): String? = lyricsReader.read(sourceUri)
}
