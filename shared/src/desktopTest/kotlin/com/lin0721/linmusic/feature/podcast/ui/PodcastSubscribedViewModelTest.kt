package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastSubscribedViewModelTest {

    private val repository = FakePodcastRepository()
    private val userPreferences = UserPreferences(InMemoryPreferencesStore())
    private val seen = PodcastSeenPreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { userPreferences.saveUserProfile(UserProfile(1, "用户", "")) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PodcastSubscribedViewModel(repository, userPreferences, seen, testResourceProvider)

    private fun page(hasMore: Boolean, vararg radios: PodcastRadio) =
        Result.success(PodcastPage(radios.toList(), hasMore))

    private fun PodcastSubscribedViewModel.success() = uiState.value as PodcastSubscribedUiState.Success

    @Test
    fun `未登录不请求并给出未登录状态`() {
        runBlocking { userPreferences.clearUserProfile() }
        val vm = viewModel()

        vm.loadIfNeeded()

        assertEquals(PodcastSubscribedUiState.NotLoggedIn, vm.uiState.value)
        assertEquals(0, repository.count("subscribed"))
    }

    @Test
    fun `默认按最近更新稳定排序，可切回默认顺序`() {
        repository.subscribedRadios = {
            page(false, testRadio(1, lastProgramAt = 100), testRadio(2, lastProgramAt = 300), testRadio(3, lastProgramAt = 100))
        }
        val vm = viewModel()

        vm.loadIfNeeded()
        // 最新一期时间相同的保持服务端顺序
        assertEquals(listOf(2L, 1L, 3L), vm.success().sortedRadios.map { it.id })

        vm.setSort(PodcastSubscribedSort.DEFAULT)
        assertEquals(listOf(1L, 2L, 3L), vm.success().sortedRadios.map { it.id })
    }

    @Test
    fun `翻页按已加载数量作偏移并追加`() {
        repository.subscribedRadios = { offset ->
            if (offset == 0) page(true, testRadio(1), testRadio(2)) else page(false, testRadio(3))
        }
        val vm = viewModel()
        vm.loadIfNeeded()
        assertTrue(vm.success().hasMore)

        vm.loadMore()

        assertEquals(1, repository.count("subscribed:2"))
        assertEquals(listOf(1L, 2L, 3L), vm.success().radios.map { it.id })
        assertEquals(false, vm.success().hasMore)
    }

    @Test
    fun `翻页失败时停止翻页并保留已有内容`() {
        repository.subscribedRadios = { offset ->
            if (offset == 0) page(true, testRadio(1), testRadio(2)) else failure()
        }
        val vm = viewModel()
        vm.loadIfNeeded()

        vm.loadMore()

        val state = vm.success()
        assertEquals(2, state.radios.size)
        assertEquals(false, state.hasMore)
        assertEquals(false, state.isLoadingMore)
    }

    @Test
    fun `首次加载失败后重试成功`() {
        repository.subscribedRadios = { failure() }
        val vm = viewModel()
        vm.loadIfNeeded()
        assertTrue(vm.uiState.value is PodcastSubscribedUiState.Error)

        repository.subscribedRadios = { page(false, testRadio(1)) }
        vm.retry()

        assertEquals(listOf(1L), vm.success().radios.map { it.id })
    }

    @Test
    fun `刷新时保留用户选择的排序`() {
        repository.subscribedRadios = { page(false, testRadio(1), testRadio(2)) }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.setSort(PodcastSubscribedSort.DEFAULT)

        vm.retry()

        assertEquals(PodcastSubscribedSort.DEFAULT, vm.success().sort)
    }

    @Test
    fun `刷新失败时保留已有内容`() {
        repository.subscribedRadios = { page(false, testRadio(1)) }
        val vm = viewModel()
        vm.loadIfNeeded()

        repository.subscribedRadios = { failure() }
        vm.retry()

        assertEquals(listOf(1L), vm.success().radios.map { it.id })
    }

    @Test
    fun `看过更新后新标记随已见时间消失`() {
        runBlocking { seen.markSeen(1, 5_000) }
        repository.subscribedRadios = { page(false, testRadio(1, lastProgramAt = 9_000)) }
        val vm = viewModel()

        vm.loadIfNeeded()
        assertEquals(setOf(1L), vm.success().updatedRadioIds)

        runBlocking { seen.markSeen(1, 9_000) }
        assertTrue(vm.success().updatedRadioIds.isEmpty())
    }
}
