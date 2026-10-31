package com.lin0721.linmusic.feature.localmusic.ui.tageditor

import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.localmusic.data.LocalLibraryRepository
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagCoverChange
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagEditor
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagForm
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagSnapshot
import com.lin0721.linmusic.feature.localmusic.data.tags.LocalTagWriteResult
import com.lin0721.linmusic.feature.localmusic.data.tags.validateTagForm
import com.lin0721.linmusic.feature.localmusic.domain.LocalTrack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val LocalTagEditorUiState.Editing.isDirty: Boolean
    get() = form.cover != LocalTagCoverChange.Keep || form != LocalTagForm(
        title = original.title,
        artist = original.artist,
        album = original.album,
        albumArtist = original.albumArtist,
        year = original.year,
        trackNumber = original.trackNumber,
        lyrics = original.lyrics
    )

private const val TAG = "LocalTagEditorVM"

sealed interface LocalTagEditorUiState {
    data object Loading : LocalTagEditorUiState
    data class Editing(
        val track: LocalTrack,
        val original: LocalTagSnapshot,
        val form: LocalTagForm,
        val coverPreview: ByteArray?,
        val isSaving: Boolean
    ) : LocalTagEditorUiState
    data class Error(val message: String) : LocalTagEditorUiState
}

sealed interface LocalTagEditorEvent {
    data object Saved : LocalTagEditorEvent
    data class RequestConsent(val intentSender: IntentSender) : LocalTagEditorEvent
    data object RequestLegacyPermission : LocalTagEditorEvent
    data object RequestReauthorize : LocalTagEditorEvent
    data class Toast(val message: String) : LocalTagEditorEvent
}

class LocalTagEditorViewModel(
    private val repository: LocalLibraryRepository,
    private val editor: LocalTagEditor
) : ViewModel() {

    private val _uiState = MutableStateFlow<LocalTagEditorUiState>(LocalTagEditorUiState.Loading)
    val uiState: StateFlow<LocalTagEditorUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<LocalTagEditorEvent>()
    val event: SharedFlow<LocalTagEditorEvent> = _event.asSharedFlow()

    fun load(uri: String) {
        viewModelScope.launch {
            _uiState.value = LocalTagEditorUiState.Loading
            val track = repository.tracks.first().find { it.uri.toString() == uri }
            if (track == null) {
                _uiState.value = LocalTagEditorUiState.Error("找不到这首歌")
                return@launch
            }
            val snapshot = editor.read(track.uri)
            if (snapshot == null) {
                _uiState.value = LocalTagEditorUiState.Error("无法读取该文件的标签")
                return@launch
            }
            val form = LocalTagForm(
                title = snapshot.title,
                artist = snapshot.artist,
                album = snapshot.album,
                albumArtist = snapshot.albumArtist,
                year = snapshot.year,
                trackNumber = snapshot.trackNumber,
                lyrics = snapshot.lyrics
            )
            _uiState.value = LocalTagEditorUiState.Editing(
                track = track,
                original = snapshot,
                form = form,
                coverPreview = null,
                isSaving = false
            )
        }
    }

    private inline fun updateForm(crossinline updater: (LocalTagForm) -> LocalTagForm) {
        _uiState.update { state ->
            if (state is LocalTagEditorUiState.Editing) {
                state.copy(form = updater(state.form))
            } else state
        }
    }

    fun updateTitle(title: String) = updateForm { it.copy(title = title) }
    fun updateArtist(artist: String) = updateForm { it.copy(artist = artist) }
    fun updateAlbum(album: String) = updateForm { it.copy(album = album) }
    fun updateAlbumArtist(albumArtist: String) = updateForm { it.copy(albumArtist = albumArtist) }
    fun updateYear(year: String) = updateForm { it.copy(year = year) }
    fun updateTrackNumber(trackNumber: String) = updateForm { it.copy(trackNumber = trackNumber) }
    fun updateLyrics(lyrics: String) = updateForm { it.copy(lyrics = lyrics) }

    fun replaceCover(bytes: ByteArray) {
        _uiState.update { state ->
            if (state is LocalTagEditorUiState.Editing) {
                state.copy(
                    form = state.form.copy(cover = LocalTagCoverChange.Replace(bytes)),
                    coverPreview = bytes
                )
            } else state
        }
    }

    fun removeCover() {
        _uiState.update { state ->
            if (state is LocalTagEditorUiState.Editing) {
                state.copy(
                    form = state.form.copy(cover = LocalTagCoverChange.Remove),
                    coverPreview = null
                )
            } else state
        }
    }

    fun save() {
        val state = _uiState.value as? LocalTagEditorUiState.Editing ?: return
        val error = validateTagForm(state.form)
        if (error != null) {
            viewModelScope.launch { _event.emit(LocalTagEditorEvent.Toast(error)) }
            return
        }
        performWrite(state)
    }

    fun retrySave() {
        val state = _uiState.value as? LocalTagEditorUiState.Editing ?: return
        performWrite(state)
    }

    private fun performWrite(state: LocalTagEditorUiState.Editing) {
        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            when (val result = editor.write(state.track.uri, state.track.source, state.form)) {
                is LocalTagWriteResult.Success -> {
                    // 文件已写成功，刷新曲库失败只影响列表展示，下次同步会补上
                    runCatching { repository.refreshAfterTagEdit(state.track) }
                        .onFailure { AppLogger.w(TAG, "标签保存后刷新曲库失败", it) }
                    _event.emit(LocalTagEditorEvent.Saved)
                }
                is LocalTagWriteResult.NeedsUserConsent -> {
                    _event.emit(LocalTagEditorEvent.RequestConsent(result.intentSender))
                }
                LocalTagWriteResult.NeedsLegacyStoragePermission -> {
                    _event.emit(LocalTagEditorEvent.RequestLegacyPermission)
                }
                LocalTagWriteResult.NeedsReauthorize -> {
                    _event.emit(LocalTagEditorEvent.RequestReauthorize)
                }
                is LocalTagWriteResult.Failed -> {
                    _event.emit(LocalTagEditorEvent.Toast(result.message))
                }
            }
            _uiState.update { current ->
                if (current is LocalTagEditorUiState.Editing) current.copy(isSaving = false) else current
            }
        }
    }

    fun onReauthorized(pickedUri: Uri) {
        val state = _uiState.value as? LocalTagEditorUiState.Editing ?: return
        if (pickedUri.toString() == state.track.uri.toString()) {
            val success = editor.takeWritePermission(pickedUri)
            if (success) {
                retrySave()
            } else {
                viewModelScope.launch { _event.emit(LocalTagEditorEvent.Toast("获取权限失败")) }
            }
        } else {
            viewModelScope.launch { _event.emit(LocalTagEditorEvent.Toast("请选择同一个文件")) }
        }
    }
}
