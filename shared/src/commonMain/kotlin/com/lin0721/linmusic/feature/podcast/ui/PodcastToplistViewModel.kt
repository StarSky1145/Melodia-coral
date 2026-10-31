package com.lin0721.linmusic.feature.podcast.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.network.ResourceProvider
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastRepository
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.playPodcastPrograms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PodcastToplistTab(val label: String) {
    RADIO("电台榜"),
    PROGRAM("节目榜")
}

data class PodcastToplistState(
    val tab: PodcastToplistTab = PodcastToplistTab.RADIO,
    val radios: PodcastSection<List<PodcastRadio>> = PodcastSection.Loading,
    // 节目榜尚未切过去时不请求，保持 Loading
    val programs: PodcastSection<List<PodcastProgram>> = PodcastSection.Loading,
    val programsHasMore: Boolean = false,
    val isLoadingMorePrograms: Boolean = false,
    val progress: Map<Long, PodcastProgressEntry> = emptyMap()
)

// 榜单二级页 ViewModel：电台榜与节目榜两个分段，各自按需加载
class PodcastToplistViewModel(
    private val podcastRepository: PodcastRepository,
    private val playbackController: PlaybackController,
    private val progressPreferences: PodcastProgressPreferences,
    private val resourceProvider: ResourceProvider
) : ViewModel() {

    private val _state = MutableStateFlow(PodcastToplistState())
    val state: StateFlow<PodcastToplistState> = _state.asStateFlow()

    private var started = false
    private var programsRequested = false

    fun loadIfNeeded() {
        if (started) return
        started = true
        observeProgress()
        loadRadios()
    }

    fun selectTab(tab: PodcastToplistTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab) }
        if (tab == PodcastToplistTab.PROGRAM && !programsRequested) loadPrograms()
    }

    fun retry() {
        when (_state.value.tab) {
            PodcastToplistTab.RADIO -> loadRadios()
            PodcastToplistTab.PROGRAM -> loadPrograms()
        }
    }

    fun loadMorePrograms() {
        val current = _state.value
        val loaded = (current.programs as? PodcastSection.Success)?.data ?: return
        if (!current.programsHasMore || current.isLoadingMorePrograms) return

        _state.update { it.copy(isLoadingMorePrograms = true) }
        viewModelScope.launch {
            val section = podcastRepository.getProgramToplist(offset = loaded.size).awaitSection(resourceProvider)
            _state.update { state ->
                val existing = (state.programs as? PodcastSection.Success)?.data ?: return@update state
                when (section) {
                    is PodcastSection.Success -> state.copy(
                        // 翻页边界上可能有重复项，按节目 id 去重
                        programs = PodcastSection.Success(
                            (existing + section.data.items).distinctBy { it.id }
                        ),
                        programsHasMore = section.data.hasMore && section.data.items.isNotEmpty(),
                        isLoadingMorePrograms = false
                    )
                    // 追加失败时停止翻页但保留已有内容
                    else -> state.copy(programsHasMore = false, isLoadingMorePrograms = false)
                }
            }
        }
    }

    // 节目榜里点哪期就从哪期起播，整份榜单作为队列
    fun playProgram(index: Int) {
        val current = _state.value
        playbackController.playPodcastPrograms(current.programs.itemsOrEmpty(), index, current.progress)
    }

    private fun observeProgress() {
        viewModelScope.launch {
            progressPreferences.entries.collect { entries ->
                _state.update { it.copy(progress = entries.associateBy { entry -> entry.songId }) }
            }
        }
    }

    private fun loadRadios() {
        viewModelScope.launch {
            _state.update {
                if (it.radios is PodcastSection.Success) it else it.copy(radios = PodcastSection.Loading)
            }
            val result = podcastRepository.getToplistRadios().awaitSection(resourceProvider)
            _state.update { it.copy(radios = settleSection(it.radios, result)) }
        }
    }

    private fun loadPrograms() {
        programsRequested = true
        viewModelScope.launch {
            _state.update {
                if (it.programs is PodcastSection.Success) it else it.copy(programs = PodcastSection.Loading)
            }
            when (val section = podcastRepository.getProgramToplist().awaitSection(resourceProvider)) {
                is PodcastSection.Success -> _state.update {
                    it.copy(
                        programs = PodcastSection.Success(section.data.items),
                        programsHasMore = section.data.hasMore && section.data.items.isNotEmpty(),
                        isLoadingMorePrograms = false
                    )
                }
                is PodcastSection.Error -> _state.update {
                    it.copy(programs = settleSection(it.programs, section))
                }
                PodcastSection.Loading -> Unit
            }
        }
    }
}
