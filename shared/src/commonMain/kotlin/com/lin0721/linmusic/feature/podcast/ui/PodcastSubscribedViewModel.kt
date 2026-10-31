package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastSubscriptionUpdates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PodcastSubscribedSort(val label: String) {
    // 最新一期越新越靠前，有更新的电台自然排在最前
    RECENT_UPDATE("最近更新"),

    // 沿用服务端返回顺序
    DEFAULT("默认顺序")
}

sealed interface PodcastSubscribedUiState {
    data object Loading : PodcastSubscribedUiState

    data object NotLoggedIn : PodcastSubscribedUiState

    data class Error(val message: String) : PodcastSubscribedUiState

    data class Success(
        // 服务端返回顺序
        val radios: List<PodcastRadio>,
        val sort: PodcastSubscribedSort = PodcastSubscribedSort.RECENT_UPDATE,
        val updatedRadioIds: Set<Long> = emptySet(),
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false
    ) : PodcastSubscribedUiState {
        // 稳定排序：最新一期时间相同的保持服务端顺序
        val sortedRadios: List<PodcastRadio>
            get() = when (sort) {
                PodcastSubscribedSort.RECENT_UPDATE -> radios.sortedByDescending { it.lastProgramCreateTimeMs }
                PodcastSubscribedSort.DEFAULT -> radios
            }
    }
}

// 「我的订阅」二级页 ViewModel
class PodcastSubscribedViewModel(
    private val podcastRepository: PodcastRepository,
    private val userPreferences: UserPreferences,
    private val seenPreferences: PodcastSeenPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastSubscribedUiState>(PodcastSubscribedUiState.Loading)
    val uiState: StateFlow<PodcastSubscribedUiState> = _uiState.asStateFlow()

    private var started = false
    private var loadJob: Job? = null

    fun loadIfNeeded() {
        if (started) return
        started = true
        observeSeen()
        load()
    }

    fun retry() = load()

    fun setSort(sort: PodcastSubscribedSort) {
        _uiState.update { if (it is PodcastSubscribedUiState.Success) it.copy(sort = sort) else it }
    }

    fun loadMore() {
        val current = _uiState.value as? PodcastSubscribedUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return

        _uiState.value = current.copy(isLoadingMore = true)
        loadJob = viewModelScope.launch {
            val section = podcastRepository.getSubscribedRadios(offset = current.radios.size)
                .awaitSection(resourceProvider)
            val latest = _uiState.value as? PodcastSubscribedUiState.Success ?: return@launch
            when (section) {
                is PodcastSection.Success -> {
                    val radios = latest.radios + section.data.items
                    val updated = seenPreferences.evaluate(radios)
                    _uiState.value = latest.copy(
                        radios = radios,
                        updatedRadioIds = updated,
                        hasMore = section.data.hasMore && section.data.items.isNotEmpty(),
                        isLoadingMore = false
                    )
                }
                // 追加失败时停止翻页但保留已有内容
                else -> _uiState.value = latest.copy(isLoadingMore = false, hasMore = false)
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (userPreferences.userProfile.first() == null) {
                _uiState.value = PodcastSubscribedUiState.NotLoggedIn
                return@launch
            }
            if (_uiState.value !is PodcastSubscribedUiState.Success) {
                _uiState.value = PodcastSubscribedUiState.Loading
            }
            when (val section = podcastRepository.getSubscribedRadios().awaitSection(resourceProvider)) {
                is PodcastSection.Success -> {
                    val radios = section.data.items
                    val updated = seenPreferences.evaluate(radios)
                    val previous = _uiState.value as? PodcastSubscribedUiState.Success
                    _uiState.value = PodcastSubscribedUiState.Success(
                        radios = radios,
                        // 刷新时保留用户已选的排序
                        sort = previous?.sort ?: PodcastSubscribedSort.RECENT_UPDATE,
                        updatedRadioIds = updated,
                        hasMore = section.data.hasMore && radios.isNotEmpty()
                    )
                }
                is PodcastSection.Error -> {
                    // 刷新失败时保留已有内容
                    if (_uiState.value !is PodcastSubscribedUiState.Success) {
                        _uiState.value = PodcastSubscribedUiState.Error(section.message)
                    }
                }
                PodcastSection.Loading -> Unit
            }
        }
    }

    // 在详情页看过更新后返回，「新」标记随已见时间同步消失
    private fun observeSeen() {
        viewModelScope.launch {
            seenPreferences.seen.collect { seen ->
                _uiState.update { state ->
                    if (state !is PodcastSubscribedUiState.Success) return@update state
                    state.copy(updatedRadioIds = PodcastSubscriptionUpdates.check(state.radios, seen).updatedRadioIds)
                }
            }
        }
    }
}
