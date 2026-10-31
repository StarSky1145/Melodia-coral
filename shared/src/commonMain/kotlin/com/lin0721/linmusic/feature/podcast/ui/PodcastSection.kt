package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

// 页面里一个独立区块的加载状态，区块之间互不拖累
sealed interface PodcastSection<out T> {
    data object Loading : PodcastSection<Nothing>

    data class Success<T>(val data: T) : PodcastSection<T>

    data class Error(val message: String) : PodcastSection<Nothing>
}

fun <T> PodcastSection<List<T>>.itemsOrEmpty(): List<T> = (this as? PodcastSection.Success)?.data.orEmpty()

internal suspend fun <T> Flow<Result<T>>.awaitSection(resourceProvider: ResourceProvider): PodcastSection<T> =
    try {
        first().fold(
            onSuccess = { PodcastSection.Success(it) },
            onFailure = { PodcastSection.Error(it.toUserMessage(resourceProvider)) }
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PodcastSection.Error(e.toUserMessage(resourceProvider))
    }

// 刷新失败时保留已有内容，只有首次加载失败才展示错误
internal fun <T> settleSection(previous: PodcastSection<T>, result: PodcastSection<T>): PodcastSection<T> =
    if (result is PodcastSection.Error && previous is PodcastSection.Success) previous else result
