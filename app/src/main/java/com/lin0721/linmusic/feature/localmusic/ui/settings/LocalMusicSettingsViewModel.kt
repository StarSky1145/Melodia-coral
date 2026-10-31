package com.lin0721.linmusic.feature.localmusic.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.DEFAULT_MIN_DURATION_SEC
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.LocalMusicSettings
import com.lin0721.linmusic.feature.localmusic.domain.AuthorizedFolder
import com.lin0721.linmusic.feature.localmusic.domain.LocalFolder
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "LocalMusicSettingsVM"

class LocalMusicSettingsViewModel(
    private val repository: LocalLibraryRepository,
    private val settings: LocalMusicSettings
) : ViewModel() {

    val minDurationSec: StateFlow<Int> = settings.scanFilter
        .map { it.minDurationSec }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_MIN_DURATION_SEC)

    val folders: StateFlow<List<LocalFolder>> = repository.folders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hiddenCount: StateFlow<Int> = repository.hiddenCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _authorizedFolders = MutableStateFlow<List<AuthorizedFolder>>(emptyList())
    val authorizedFolders: StateFlow<List<AuthorizedFolder>> = _authorizedFolders.asStateFlow()

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        refreshAuthorizedFolders()
    }

    fun setMinDurationSec(seconds: Int) {
        viewModelScope.launch { settings.setMinDurationSec(seconds) }
    }

    fun setFolderIncluded(folder: LocalFolder, included: Boolean) {
        viewModelScope.launch { settings.setFolderExcluded(folder.path, !included) }
    }

    fun addAuthorizedFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isImporting.value = true
            runCatching { repository.importFolder(treeUri) }
                .onSuccess { result ->
                    _toastEvent.emit(
                        if (result.addedCount > 0) "已导入 ${result.addedCount} 首歌曲" else "文件夹已授权，没有新的歌曲"
                    )
                }
                .onFailure {
                    AppLogger.e(TAG, "授权文件夹导入失败", it)
                    _toastEvent.emit("导入失败：${it.message ?: "未知错误"}")
                }
            _isImporting.value = false
            refreshAuthorizedFolders()
        }
    }

    fun removeAuthorizedFolder(folder: AuthorizedFolder) {
        viewModelScope.launch {
            runCatching { repository.removeAuthorizedFolder(folder.treeUri) }
                .onSuccess { _toastEvent.emit("已移除 ${folder.name}") }
                .onFailure {
                    AppLogger.e(TAG, "移除授权文件夹失败", it)
                    _toastEvent.emit("移除失败")
                }
            refreshAuthorizedFolders()
        }
    }

    private fun refreshAuthorizedFolders() {
        viewModelScope.launch {
            _authorizedFolders.value = runCatching { repository.listAuthorizedFolders() }
                .onFailure { AppLogger.w(TAG, "读取授权文件夹失败", it) }
                .getOrDefault(emptyList())
        }
    }
}
