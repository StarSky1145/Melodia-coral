package com.lin0721.linmusic.feature.localmusic.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lin0721.linmusic.feature.localmusic.domain.LocalScanFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.localMusicSettingsDataStore by preferencesDataStore(name = "local_music_settings")

const val DEFAULT_MIN_DURATION_SEC = 60

class LocalMusicSettings(private val context: Context) {

    companion object {
        private val KEY_MIN_DURATION_SEC = intPreferencesKey("min_duration_sec")
        private val KEY_EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
    }

    val scanFilter: Flow<LocalScanFilter> = context.localMusicSettingsDataStore.data.map { prefs ->
        LocalScanFilter(
            minDurationSec = prefs[KEY_MIN_DURATION_SEC] ?: DEFAULT_MIN_DURATION_SEC,
            excludedFolders = prefs[KEY_EXCLUDED_FOLDERS].orEmpty()
        )
    }

    suspend fun setMinDurationSec(seconds: Int) {
        context.localMusicSettingsDataStore.edit { it[KEY_MIN_DURATION_SEC] = seconds.coerceAtLeast(0) }
    }

    suspend fun setFolderExcluded(folderPath: String, excluded: Boolean) {
        context.localMusicSettingsDataStore.edit { prefs ->
            val current = prefs[KEY_EXCLUDED_FOLDERS].orEmpty()
            prefs[KEY_EXCLUDED_FOLDERS] = if (excluded) current + folderPath else current - folderPath
        }
    }
}
