package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastToplistViewModelTest {

    private val repository = FakePodcastRepository()
    private val controller = FakePlaybackController()
    private val progress = PodcastProgressPreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PodcastToplistViewModel(repository, controller, progress, testResourceProvider)

    private fun page(hasMore: Boolean, vararg programs: PodcastProgram) =
        Result.success(PodcastPage(programs.toList(), hasMore))

    @Test
    fun `首次只加载电台榜，切到节目榜才加载节目`() {
        repository.toplistRadios = { Result.success(listOf(testRadio(1))) }
        repository.programToplist = { page(false, testProgram(1)) }
        val vm = viewModel()

        vm.loadIfNeeded()
        assertEquals(0, repository.count("programToplist"))
        assertEquals(listOf(1L), vm.state.value.radios.itemsOrEmpty().map { it.id })

        vm.selectTab(PodcastToplistTab.PROGRAM)
        vm.selectTab(PodcastToplistTab.RADIO)
        vm.selectTab(PodcastToplistTab.PROGRAM)

        assertEquals(1, repository.count("programToplist"))
        assertEquals(listOf(1L), vm.state.value.programs.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `节目榜翻页追加并按节目id去重`() {
        repository.programToplist = { offset ->
            if (offset == 0) page(true, testProgram(1), testProgram(2)) else page(false, testProgram(2), testProgram(3))
        }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.selectTab(PodcastToplistTab.PROGRAM)
        assertTrue(vm.state.value.programsHasMore)

        vm.loadMorePrograms()

        assertEquals(1, repository.count("programToplist:2"))
        assertEquals(listOf(1L, 2L, 3L), vm.state.value.programs.itemsOrEmpty().map { it.id })
        assertEquals(false, vm.state.value.programsHasMore)
    }

    @Test
    fun `节目榜翻页失败时停止翻页并保留已有内容`() {
        repository.programToplist = { offset ->
            if (offset == 0) page(true, testProgram(1)) else failure()
        }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.selectTab(PodcastToplistTab.PROGRAM)

        vm.loadMorePrograms()

        assertEquals(1, vm.state.value.programs.itemsOrEmpty().size)
        assertEquals(false, vm.state.value.programsHasMore)
        assertEquals(false, vm.state.value.isLoadingMorePrograms)
    }

    @Test
    fun `当前分段失败后重试只重拉当前分段`() {
        repository.programToplist = { failure() }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.selectTab(PodcastToplistTab.PROGRAM)
        assertTrue(vm.state.value.programs is PodcastSection.Error)

        repository.programToplist = { page(false, testProgram(5)) }
        vm.retry()

        assertEquals(listOf(5L), vm.state.value.programs.itemsOrEmpty().map { it.id })
        assertEquals(1, repository.count("toplistRadios"))
    }

    @Test
    fun `点击节目榜从该期起播`() {
        repository.programToplist = { page(false, testProgram(1), testProgram(2)) }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.selectTab(PodcastToplistTab.PROGRAM)

        vm.playProgram(1)

        val call = controller.queueCalls.single()
        assertEquals(1, call.startIndex)
        assertEquals(listOf(10L, 20L), call.items.map { it.songId })
    }
}
