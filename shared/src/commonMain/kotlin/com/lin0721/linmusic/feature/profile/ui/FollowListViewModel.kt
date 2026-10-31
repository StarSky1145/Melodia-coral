package com.lin0721.linmusic.feature.profile.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.feature.profile.data.ProfileRepository
import com.lin0721.linmusic.feature.profile.domain.ProfileFollowUserItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val PAGE_SIZE = 30

@Serializable
enum class FollowListMode {
    FOLLOWS,
    FOLLOWEDS
}

sealed interface FollowListUiState {
    data object Loading : FollowListUiState

    data class Success(
        val users: List<ProfileFollowUserItem>,
        val hasMore: Boolean,
        val isLoadingMore: Boolean = false
    ) : FollowListUiState

    data class Error(val message: String) : FollowListUiState
}

class FollowListViewModel(
    private val profileRepository: ProfileRepository
) : ViewModel() {

    // 同 ProfileViewModel：uid/mode 不放进构造参数，避免 koinViewModel() 缓存实例导致
    private var uid: Long = 0L
    private var mode: FollowListMode = FollowListMode.FOLLOWS

    private val _uiState = MutableStateFlow<FollowListUiState>(FollowListUiState.Loading)
    val uiState: StateFlow<FollowListUiState> = _uiState.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    private var offset = 0

    // 页面重新进入组合（如从用户主页返回）时复用已有数据，仅参数变化或加载失败时才请求
    fun loadIfNeeded(uid: Long, mode: FollowListMode) {
        val state = _uiState.value
        val inProgressOrLoaded = state is FollowListUiState.Loading || state is FollowListUiState.Success
        if (this.uid == uid && this.mode == mode && inProgressOrLoaded) return
        load(uid, mode)
    }

    // 由 FollowListScreen 的 LaunchedEffect(uid, mode) 调用
    fun load(uid: Long, mode: FollowListMode) {
        this.uid = uid
        this.mode = mode
        load()
    }

    fun load() {
        _uiState.value = FollowListUiState.Loading
        offset = 0
        viewModelScope.launch {
            val flow = if (mode == FollowListMode.FOLLOWS) {
                profileRepository.getUserFollows(uid, offset = 0, limit = PAGE_SIZE)
            } else {
                profileRepository.getUserFolloweds(uid, offset = 0, limit = PAGE_SIZE)
            }

            flow.first()
                .onSuccess { page ->
                    offset = page.users.size
                    _uiState.value = FollowListUiState.Success(
                        users = page.users,
                        hasMore = page.hasMore,
                        isLoadingMore = false
                    )
                }
                .onFailure { error ->
                    val defaultMsg = if (mode == FollowListMode.FOLLOWS) "获取关注列表失败" else "获取粉丝列表失败"
                    _uiState.value = FollowListUiState.Error(error.message ?: defaultMsg)
                }
        }
    }

    fun retry() {
        load()
    }

    fun toggleFollow(targetUid: Long) {
        val state = _uiState.value as? FollowListUiState.Success ?: return
        val user = state.users.firstOrNull { it.uid == targetUid } ?: return
        val targetFollowed = !user.isFollowedByMe
        viewModelScope.launch {
            val request = if (targetFollowed) {
                profileRepository.followUser(targetUid)
            } else {
                profileRepository.unfollowUser(targetUid)
            }
            request.first()
                .onSuccess {
                    val latest = _uiState.value as? FollowListUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        users = latest.users.map { if (it.uid == targetUid) it.copy(isFollowedByMe = targetFollowed) else it }
                    )
                    _toastEvent.emit(if (targetFollowed) "关注成功" else "已取消关注")
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: if (targetFollowed) "关注失败" else "取消关注失败")
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value as? FollowListUiState.Success ?: return
        if (!state.hasMore || state.isLoadingMore) return
        _uiState.value = state.copy(isLoadingMore = true)
        viewModelScope.launch {
            val flow = if (mode == FollowListMode.FOLLOWS) {
                profileRepository.getUserFollows(uid, offset = offset, limit = PAGE_SIZE)
            } else {
                profileRepository.getUserFolloweds(uid, offset = offset, limit = PAGE_SIZE)
            }

            flow.first()
                .onSuccess { page ->
                    offset += page.users.size
                    val latest = _uiState.value as? FollowListUiState.Success ?: return@onSuccess
                    _uiState.value = latest.copy(
                        users = latest.users + page.users,
                        hasMore = page.hasMore,
                        isLoadingMore = false
                    )
                }
                .onFailure { error ->
                    _toastEvent.emit(error.message ?: "加载更多失败")
                    val latest = _uiState.value as? FollowListUiState.Success ?: return@onFailure
                    _uiState.value = latest.copy(isLoadingMore = false)
                }
        }
    }
}
