package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.network.toUserMessage
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 播放器里当前播客节目的状态，供全屏页、迷你条与桌面播放栏切换成播客专属控件
data class PodcastPlayerState(
    val isPodcast: Boolean = false,
    val radioId: Long = 0,
    val radioName: String = "",
    val subscribed: Boolean = false,
    val isSubscribing: Boolean = false
) {
    // 队列项没带电台 id（旧队列）时无法订阅
    val canSubscribe: Boolean get() = isPodcast && radioId > 0
}

private data class RadioInfo(val name: String, val subscribed: Boolean)

// 跟随播放器的当前节目，维护其所属电台的订阅状态并提供订阅切换；全局单例，迷你条与全屏页共用同一份状态
class PodcastPlayerController(
    private val scope: CoroutineScope,
    private val repository: PodcastRepository,
    private val userPreferences: UserPreferences,
    private val playbackController: PlaybackController,
    private val resourceProvider: ResourceProvider
) {
    private val _state = MutableStateFlow(PodcastPlayerState())
    val state: StateFlow<PodcastPlayerState> = _state.asStateFlow()

    // 电台详情里取到的名称与订阅状态；登录账号变化后整体作废
    private val cache = HashMap<Long, RadioInfo>()
    private var cachedUid: Long? = null

    init {
        scope.launch {
            combine(
                playbackController.currentQueueItem,
                playbackController.playContext,
                userPreferences.userProfile.map { it?.uid }.distinctUntilChanged()
            ) { item, context, uid -> Triple(item, context, uid) }
                .collectLatest { (item, context, uid) ->
                    if (uid != cachedUid) {
                        cache.clear()
                        cachedUid = uid
                    }
                    if (context != PlaybackController.CONTEXT_PODCAST) {
                        _state.value = PodcastPlayerState()
                        return@collectLatest
                    }
                    val radioId = item?.radioId ?: 0L
                    val info = cache[radioId]
                    _state.value = PodcastPlayerState(
                        isPodcast = true,
                        radioId = radioId,
                        radioName = info?.name ?: item?.artist?.substringBefore(" · ").orEmpty(),
                        subscribed = info?.subscribed ?: false
                    )
                    if (radioId > 0 && info == null) loadRadio(radioId)
                }
        }
    }

    // 订阅或取消订阅当前节目所属电台。订阅接口未登录时同样返回成功却不生效，必须先拦住
    fun toggleSubscribe(onToast: suspend (String) -> Unit) {
        val current = _state.value
        if (!current.canSubscribe || current.isSubscribing) return
        val radioId = current.radioId

        scope.launch {
            if (userPreferences.userProfile.first() == null) {
                onToast("登录后才能订阅电台")
                return@launch
            }
            val target = !current.subscribed
            _state.update { if (it.radioId == radioId) it.copy(isSubscribing = true) else it }

            val result = try {
                repository.setRadioSubscribed(radioId, target).first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (result.isSuccess) {
                cache[radioId] = RadioInfo(cache[radioId]?.name ?: current.radioName, target)
                _state.update {
                    if (it.radioId == radioId) it.copy(subscribed = target, isSubscribing = false) else it
                }
                onToast(if (target) "已订阅" else "已取消订阅")
            } else {
                _state.update { if (it.radioId == radioId) it.copy(isSubscribing = false) else it }
                onToast(result.exceptionOrNull()?.toUserMessage(resourceProvider).orEmpty().ifBlank { "操作失败" })
            }
        }
    }

    private suspend fun loadRadio(radioId: Long) {
        val detail = fetch(repository.getRadioDetail(radioId)) ?: return
        cache[radioId] = RadioInfo(detail.name, detail.subscribed)
        _state.update {
            if (it.radioId == radioId) it.copy(radioName = detail.name, subscribed = detail.subscribed) else it
        }
    }

    private suspend fun <T> fetch(flow: Flow<Result<T>>): T? = try {
        flow.first().getOrNull()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
