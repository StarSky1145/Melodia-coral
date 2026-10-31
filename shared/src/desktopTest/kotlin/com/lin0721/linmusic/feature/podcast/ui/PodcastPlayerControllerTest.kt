package com.lin0721.linmusic.feature.podcast.ui

import com.lin0721.linmusic.core.auth.UserPreferences
import com.lin0721.linmusic.core.auth.UserProfile
import com.lin0721.linmusic.core.player.PlaybackController
import com.lin0721.linmusic.core.player.QueueItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PodcastPlayerControllerTest {

    private val repository = FakePodcastRepository()
    private val controller = FakePlaybackController()
    private val userPreferences = UserPreferences(InMemoryPreferencesStore())
    private val toasts = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun player() = PodcastPlayerController(
        scope = CoroutineScope(UnconfinedTestDispatcher()),
        repository = repository,
        userPreferences = userPreferences,
        playbackController = controller,
        resourceProvider = testResourceProvider
    )

    private fun logIn() = runBlocking { userPreferences.saveUserProfile(UserProfile(1, "用户", "")) }

    private fun playPodcast(radioId: Long, artist: String = "闲聊时间 · 小林") {
        controller.mutableQueueItem.value = QueueItem(10, "节目", artist, "http://p/1.jpg", radioId = radioId)
        controller.mutablePlayContext.value = PlaybackController.CONTEXT_PODCAST
    }

    @Test
    fun `非播客播放时不是播客状态`() {
        val player = player()
        controller.mutableQueueItem.value = QueueItem(1, "歌", "歌手", "")
        controller.mutablePlayContext.value = "搜索"

        assertFalse(player.state.value.isPodcast)
        assertEquals(0, repository.count("detail:"))
    }

    @Test
    fun `播放播客时拉取电台名称与订阅状态`() {
        repository.radioDetail = { Result.success(testRadioDetail(it, subscribed = true).copy(name = "闲聊时间")) }
        val player = player()

        playPodcast(radioId = 7)

        val state = player.state.value
        assertTrue(state.isPodcast)
        assertTrue(state.canSubscribe)
        assertEquals("闲聊时间", state.radioName)
        assertTrue(state.subscribed)
    }

    @Test
    fun `详情未返回前电台名取自副标题`() {
        repository.radioDetail = { failure() }
        val player = player()

        playPodcast(radioId = 7, artist = "某电台 · 某主播")

        assertEquals("某电台", player.state.value.radioName)
        assertFalse(player.state.value.subscribed)
    }

    @Test
    fun `队列项没有电台id时无法订阅且不拉取详情`() {
        val player = player()

        playPodcast(radioId = 0)

        assertTrue(player.state.value.isPodcast)
        assertFalse(player.state.value.canSubscribe)
        assertEquals(0, repository.count("detail:"))

        player.toggleSubscribe { toasts += it }
        assertEquals(0, repository.count("subscribe:"))
    }

    @Test
    fun `未登录订阅被拦住`() {
        val player = player()
        playPodcast(radioId = 7)

        player.toggleSubscribe { toasts += it }

        assertEquals(0, repository.count("subscribe:"))
        assertEquals(listOf("登录后才能订阅电台"), toasts)
    }

    @Test
    fun `登录后订阅成功更新状态并提示`() {
        logIn()
        val player = player()
        playPodcast(radioId = 7)

        player.toggleSubscribe { toasts += it }

        assertEquals(1, repository.count("subscribe:7:true"))
        assertTrue(player.state.value.subscribed)
        assertFalse(player.state.value.isSubscribing)
        assertEquals(listOf("已订阅"), toasts)

        player.toggleSubscribe { toasts += it }
        assertEquals(1, repository.count("subscribe:7:false"))
        assertFalse(player.state.value.subscribed)
    }

    @Test
    fun `订阅失败保持原状态`() {
        logIn()
        repository.setSubscribed = { _, _ -> failure() }
        val player = player()
        playPodcast(radioId = 7)

        player.toggleSubscribe { toasts += it }

        assertFalse(player.state.value.subscribed)
        assertFalse(player.state.value.isSubscribing)
        assertEquals(1, toasts.size)
    }

    @Test
    fun `切到另一个电台的节目时重新拉取并缓存`() {
        repository.radioDetail = { id -> Result.success(testRadioDetail(id, subscribed = id == 8L)) }
        val player = player()

        playPodcast(radioId = 7)
        assertFalse(player.state.value.subscribed)
        playPodcast(radioId = 8)
        assertTrue(player.state.value.subscribed)
        // 切回已缓存的电台不再请求
        playPodcast(radioId = 7)
        assertEquals(1, repository.count("detail:7"))
    }

    @Test
    fun `退出播客上下文后状态复位`() {
        val player = player()
        playPodcast(radioId = 7)
        assertTrue(player.state.value.isPodcast)

        controller.mutablePlayContext.value = "搜索"

        assertEquals(PodcastPlayerState(), player.state.value)
    }
}
