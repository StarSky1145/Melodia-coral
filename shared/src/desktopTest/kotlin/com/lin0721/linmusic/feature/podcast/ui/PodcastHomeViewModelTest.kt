package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.feature.podcast.data.PodcastProgressPreferences
import com.lin0721.linmusic.feature.podcast.data.PodcastSeenPreferences
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategory
import com.lin0721.linmusic.feature.podcast.domain.PodcastCategoryGroup
import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgram
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import kotlinx.coroutines.CompletableDeferred
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
class PodcastHomeViewModelTest {

    private val repository = FakePodcastRepository()
    private val controller = FakePlaybackController()
    private val userPreferences = UserPreferences(InMemoryPreferencesStore())
    private val progress = PodcastProgressPreferences(InMemoryPreferencesStore())
    private val seen = PodcastSeenPreferences(InMemoryPreferencesStore())

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PodcastHomeViewModel(
        repository, controller, userPreferences, progress, seen, testResourceProvider
    )

    private fun logIn() = runBlocking { userPreferences.saveUserProfile(UserProfile(1, "用户", "")) }

    private fun subscribedPage(vararg radios: PodcastRadio) =
        Result.success(PodcastPage(radios.toList(), false))

    @Test
    fun `首次加载拉取公共区块且重复调用不重拉`() {
        repository.recommendPrograms = { Result.success(listOf(testProgram(1))) }
        repository.categories = { Result.success(listOf(PodcastCategory(3, "情感"))) }
        val vm = viewModel()

        vm.loadIfNeeded()
        vm.loadIfNeeded()

        val state = vm.state.value
        assertEquals(listOf(1L), state.picks.itemsOrEmpty().map { it.id })
        assertEquals(listOf("情感"), state.categories.map { it.name })
        assertEquals(1, repository.count("recommendPrograms:null"))
        assertEquals(1, repository.count("categoryGroups"))
    }

    @Test
    fun `单个区块失败不影响其它区块`() {
        repository.recommendPrograms = { failure() }
        repository.categoryGroups = {
            Result.success(listOf(PodcastCategoryGroup(3, "情感", listOf(testRadio(1)))))
        }
        val vm = viewModel()

        vm.loadIfNeeded()

        val state = vm.state.value
        assertTrue(state.picks is PodcastSection.Error)
        assertEquals(1, state.categoryGroups.itemsOrEmpty().size)
    }

    @Test
    fun `刷新失败时保留已有内容`() {
        repository.recommendPrograms = { Result.success(listOf(testProgram(1))) }
        val vm = viewModel()
        vm.loadIfNeeded()

        repository.recommendPrograms = { failure() }
        vm.refresh()

        assertEquals(listOf(1L), vm.state.value.picks.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `重试失败区块后恢复`() {
        repository.toplistRadios = { failure() }
        val vm = viewModel()
        vm.loadIfNeeded()
        assertTrue(vm.state.value.toplistRadios is PodcastSection.Error)

        repository.toplistRadios = { Result.success(listOf(testRadio(5))) }
        vm.retryToplistRadios()

        assertEquals(listOf(5L), vm.state.value.toplistRadios.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `未登录不请求订阅并给出空区块`() {
        val vm = viewModel()

        vm.loadIfNeeded()

        assertEquals(false, vm.state.value.isLoggedIn)
        assertEquals(PodcastSection.Success(emptyList<Any>()), vm.state.value.subscribed)
        assertEquals(0, repository.count("subscribed"))
    }

    @Test
    fun `登录后加载订阅且首次不标记更新`() {
        logIn()
        repository.subscribedRadios = { subscribedPage(testRadio(1, lastProgramAt = 5_000)) }
        val vm = viewModel()

        vm.loadIfNeeded()

        assertEquals(listOf(1L), vm.state.value.subscribed.itemsOrEmpty().map { it.id })
        assertTrue(vm.state.value.updatedRadioIds.isEmpty())
    }

    @Test
    fun `出新一期后标记更新，看过后清除`() {
        logIn()
        repository.subscribedRadios = { subscribedPage(testRadio(1, lastProgramAt = 5_000)) }
        val vm = viewModel()
        vm.loadIfNeeded()

        repository.subscribedRadios = { subscribedPage(testRadio(1, lastProgramAt = 9_000)) }
        vm.retrySubscribed()
        assertEquals(setOf(1L), vm.state.value.updatedRadioIds)

        runBlocking { seen.markSeen(1, 9_000) }
        assertTrue(vm.state.value.updatedRadioIds.isEmpty())
    }

    @Test
    fun `运行中登录后加载订阅，退出后清空`() {
        repository.subscribedRadios = { subscribedPage(testRadio(1, lastProgramAt = 5_000)) }
        val vm = viewModel()
        vm.loadIfNeeded()
        assertEquals(0, repository.count("subscribed"))

        logIn()
        assertEquals(true, vm.state.value.isLoggedIn)
        assertEquals(1, vm.state.value.subscribed.itemsOrEmpty().size)

        runBlocking { userPreferences.clearUserProfile() }
        assertTrue(vm.state.value.subscribed.itemsOrEmpty().isEmpty())
        assertTrue(vm.state.value.updatedRadioIds.isEmpty())
    }

    @Test
    fun `选分类筛选加载该分类热门电台，快速切换时丢弃晚到响应`() {
        val slow = CompletableDeferred<Result<PodcastPage<PodcastRadio>>>()
        repository.categoryHotRadios = { cate, _ ->
            if (cate == 1L) slow.await() else Result.success(PodcastPage(listOf(testRadio(2)), false))
        }
        val vm = viewModel()
        vm.loadIfNeeded()

        vm.selectFilter(PodcastFilter.Category(1))
        vm.selectFilter(PodcastFilter.Category(2))
        slow.complete(Result.success(PodcastPage(listOf(testRadio(1)), false)))

        assertEquals(PodcastFilter.Category(2), vm.state.value.filter)
        assertEquals(listOf(2L), vm.state.value.categoryRadios.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `分类电台加载失败后重试成功`() {
        repository.categoryHotRadios = { _, _ -> failure() }
        val vm = viewModel()
        vm.loadIfNeeded()
        vm.selectFilter(PodcastFilter.Category(3))
        assertTrue(vm.state.value.categoryRadios is PodcastSection.Error)

        repository.categoryHotRadios = { _, _ -> Result.success(PodcastPage(listOf(testRadio(7)), false)) }
        vm.retryCategoryRadios()

        assertEquals(listOf(7L), vm.state.value.categoryRadios.itemsOrEmpty().map { it.id })
    }

    @Test
    fun `继续收听只含未听完的节目且最近优先`() {
        runBlocking {
            progress.upsert(testProgress(1, updatedAtMs = 1))
            // 进度接近末尾，视为听完
            progress.upsert(testProgress(2, positionMs = 995_000, updatedAtMs = 2))
            progress.upsert(testProgress(3, updatedAtMs = 3))
        }
        val vm = viewModel()

        vm.loadIfNeeded()

        assertEquals(listOf(3L, 1L), vm.state.value.continueListening.map { it.songId })
    }

    @Test
    fun `继续收听最多展示固定条数`() {
        runBlocking { repeat(12) { progress.upsert(testProgress(it + 1L, updatedAtMs = it.toLong())) } }
        val vm = viewModel()

        vm.loadIfNeeded()

        assertEquals(8, vm.state.value.continueListening.size)
    }

    @Test
    fun `播放推荐节目时从未听完的进度续播`() {
        repository.recommendPrograms = { Result.success(listOf(testProgram(1), testProgram(2))) }
        runBlocking {
            progress.upsert(testProgress(10, positionMs = 123_000))
            progress.upsert(testProgress(20, positionMs = 995_000))
        }
        val vm = viewModel()
        vm.loadIfNeeded()

        vm.playPicks(0)
        vm.playPicks(1)

        val first = controller.queueCalls[0]
        assertEquals(PlaybackController.CONTEXT_PODCAST, first.playContext)
        assertEquals(2, first.items.size)
        assertEquals(123_000L, first.startPositionMs)
        // 第二期已听完，从头播
        assertEquals(0L, controller.queueCalls[1].startPositionMs)
    }

    @Test
    fun `继续收听从本地进度续播单曲`() {
        val vm = viewModel()
        val entry = testProgress(30, positionMs = 456_000)

        vm.resume(entry)

        val call = controller.queueCalls.single()
        assertEquals(listOf(30L), call.items.map { it.songId })
        assertEquals(456_000L, call.startPositionMs)
        assertEquals(PlaybackController.CONTEXT_PODCAST, call.playContext)
    }
}
