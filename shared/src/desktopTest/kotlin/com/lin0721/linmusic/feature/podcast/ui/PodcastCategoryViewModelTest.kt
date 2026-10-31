package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.feature.podcast.domain.PodcastPage
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import kotlinx.coroutines.CompletableDeferred
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
class PodcastCategoryViewModelTest {

    private val repository = FakePodcastRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = PodcastCategoryViewModel(repository, testResourceProvider)

    private fun page(hasMore: Boolean, vararg radios: PodcastRadio) =
        Result.success(PodcastPage(radios.toList(), hasMore))

    private fun PodcastCategoryViewModel.success() = uiState.value as PodcastCategoryUiState.Success

    @Test
    fun `加载分类热门电台`() {
        repository.categoryHotRadios = { _, _ -> page(true, testRadio(1), testRadio(2)) }
        val vm = viewModel()

        vm.load(3)

        val state = vm.success()
        assertEquals(3L, state.categoryId)
        assertEquals(listOf(1L, 2L), state.radios.map { it.id })
        assertTrue(state.hasMore)
    }

    @Test
    fun `同一分类重复进入不重拉`() {
        repository.categoryHotRadios = { _, _ -> page(false, testRadio(1)) }
        val vm = viewModel()

        vm.load(3)
        vm.load(3)

        assertEquals(1, repository.count("categoryHot:3:0"))
    }

    @Test
    fun `电台列表失败即整页失败`() {
        repository.categoryHotRadios = { _, _ -> failure() }
        val vm = viewModel()

        vm.load(3)

        assertTrue(vm.uiState.value is PodcastCategoryUiState.Error)
    }

    @Test
    fun `翻页按已加载数量作偏移，失败时停止翻页`() {
        repository.categoryHotRadios = { _, offset ->
            when (offset) {
                0 -> page(true, testRadio(1), testRadio(2))
                2 -> page(true, testRadio(3))
                else -> failure()
            }
        }
        val vm = viewModel()
        vm.load(3)

        vm.loadMore()
        assertEquals(listOf(1L, 2L, 3L), vm.success().radios.map { it.id })

        vm.loadMore()
        assertEquals(3, vm.success().radios.size)
        assertEquals(false, vm.success().hasMore)
    }

    @Test
    fun `快速切换分类时丢弃晚到的响应`() {
        val slow = CompletableDeferred<Result<PodcastPage<PodcastRadio>>>()
        repository.categoryHotRadios = { cate, _ -> if (cate == 1L) slow.await() else page(false, testRadio(2)) }
        val vm = viewModel()

        vm.load(1)
        vm.load(2)
        slow.complete(page(false, testRadio(1)))

        assertEquals(2L, vm.success().categoryId)
        assertEquals(listOf(2L), vm.success().radios.map { it.id })
    }
}
