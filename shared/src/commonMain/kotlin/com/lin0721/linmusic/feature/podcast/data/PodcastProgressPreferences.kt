package com.lin0721.linmusic.feature.podcast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressEntry
import com.lin0721.linmusic.feature.podcast.domain.PodcastProgressRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val TAG = "PodcastProgressPreferences"

// 本地播客收听进度，最近更新优先、按节目去重、限量
class PodcastProgressPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_PROGRESS = stringPreferencesKey("podcast_progress")
        private val json = Json { ignoreUnknownKeys = true }
    }

    // 读取失败按无记录处理，不让收听进度的问题波及页面
    val entries: Flow<List<PodcastProgressEntry>> = dataStore.data
        .map { prefs -> decode(prefs[KEY_PROGRESS]) }
        .catch { e ->
            AppLogger.w(TAG, "收听进度读取失败", e)
            emit(emptyList())
        }

    suspend fun upsert(entry: PodcastProgressEntry) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_PROGRESS])
            val updated = (listOf(entry) + current.filterNot { it.songId == entry.songId })
                .take(PodcastProgressRules.MAX_ENTRIES)
            prefs[KEY_PROGRESS] = json.encodeToString(updated)
        }
    }

    suspend fun remove(songId: Long) {
        dataStore.edit { prefs ->
            val current = decode(prefs[KEY_PROGRESS])
            prefs[KEY_PROGRESS] = json.encodeToString(current.filterNot { it.songId == songId })
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_PROGRESS) }
    }

    private fun decode(raw: String?): List<PodcastProgressEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<PodcastProgressEntry>>(raw) }
            .onFailure { AppLogger.w(TAG, "收听进度反序列化失败", it) }
            .getOrDefault(emptyList())
    }
}
