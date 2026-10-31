package com.lin0721.linmusic.core.cache

import com.lin0721.linmusic.core.network.OnlineStateProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files

class FakeOnlineState(initial: Boolean = true) : OnlineStateProvider {
    private val state = MutableStateFlow(initial)
    override val online: StateFlow<Boolean> = state

    fun set(value: Boolean) {
        state.value = value
    }
}

fun testMetadataCache(dir: File = Files.createTempDirectory("melodia-meta").toFile(), uid: () -> Long = { 0L }) =
    MetadataCache(dir) { uid() }

fun testOfflineFallback(online: FakeOnlineState = FakeOnlineState(), cache: MetadataCache = testMetadataCache()) =
    OfflineFallback(cache, online)
