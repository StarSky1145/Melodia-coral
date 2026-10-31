package com.lin0721.linmusic.feature.localmusic.data.legacy

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.importedMusicDataStore by preferencesDataStore(name = "imported_music_prefs")

@Serializable
data class LegacyImportedTrackRecord(
    val uriString: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long
)

// 仅用于把旧版 DataStore 导入记录迁移进 Room
class LegacyImportedMusicStore(private val context: Context) {

    companion object {
        private val KEY_RECORDS = stringPreferencesKey("imported_track_records")
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun readAll(): List<LegacyImportedTrackRecord> {
        val raw = context.importedMusicDataStore.data.first()[KEY_RECORDS]
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<LegacyImportedTrackRecord>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun clear() {
        context.importedMusicDataStore.edit { it.remove(KEY_RECORDS) }
    }
}
