package com.lin0721.linmusic.core.localmusic

import android.net.Uri

// core 只依赖此接口，实现在 feature/localmusic
interface LocalMusicApi {

    suspend fun coverUriFor(sourceUri: Uri): Uri?

    suspend fun readLyrics(sourceUri: String): String?
}
