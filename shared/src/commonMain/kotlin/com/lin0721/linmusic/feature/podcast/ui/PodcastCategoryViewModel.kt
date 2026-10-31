package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PodcastCategoryUiState {
    data object Loading : PodcastCategoryUiState

    data class Error(val message: String) : PodcastCategoryUiState

    data class Success(
        val categoryId: Long,
        val radios: List<PodcastRadio>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : PodcastCategoryUiState
}

// 分类二级页 ViewModel：该分类的热门电台，可翻页
class PodcastCategoryViewModel(
    private val podcastRepository: PodcastRepository,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow<PodcastCategoryUiState>(PodcastCategoryUiState.Loading)
    val uiState: StateFlow<PodcastCategoryUiState> = _uiState.asStateFlow()

    private var categoryId: Long? = null

    // 同一分类重复进入不必重拉
    fun load(categoryId: Long) {
        if (this.categoryId == categoryId && _uiState.value is PodcastCategoryUiState.Success) return
        this.categoryId = categoryId
        reload(categoryId)
    }

    fun retry() {
        categoryId?.let { reload(it) }
    }

    fun loadMore() {
        val current = _uiState.value as? PodcastCategoryUiState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return

        _uiState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch {
            val section = podcastRepository.getCategoryHotRadios(current.categoryId, offset = current.radios.size)
                .awaitSection(resourceProvider)
            _uiState.update { state ->
                if (state !is PodcastCategoryUiState.Success || state.categoryId != current.categoryId) return@update state
                when (section) {
                    is PodcastSection.Success -> state.copy(
                        radios = state.radios + section.data.items,
                        hasMore = section.data.hasMore && section.data.items.isNotEmpty(),
                        isLoadingMore = false
                    )
                    // 追加失败时停止翻页但保留已有内容
                    else -> state.copy(isLoadingMore = false, hasMore = false)
                }
            }
        }
    }

    private fun reload(categoryId: Long) {
        _uiState.value = PodcastCategoryUiState.Loading
        viewModelScope.launch {
            val radios = podcastRepository.getCategoryHotRadios(categoryId).awaitSection(resourceProvider)
            // 期间又切到别的分类，晚到的结果丢弃
            if (this@PodcastCategoryViewModel.categoryId != categoryId) return@launch

            _uiState.value = when (radios) {
                is PodcastSection.Success -> PodcastCategoryUiState.Success(
                    categoryId = categoryId,
                    radios = radios.data.items,
                    hasMore = radios.data.hasMore && radios.data.items.isNotEmpty()
                )
                is PodcastSection.Error -> PodcastCategoryUiState.Error(radios.message)
                PodcastSection.Loading -> PodcastCategoryUiState.Loading
            }
        }
    }
}
