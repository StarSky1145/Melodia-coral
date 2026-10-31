package com.lin0721.linmusic.feature.podcast.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lin0721.linmusic.core.log.AppLogger
import com.lin0721.linmusic.feature.podcast.domain.PodcastRadio
import com.lin0721.linmusic.feature.podcast.domain.PodcastSubscriptionUpdates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

private const val TAG = "PodcastSeenPreferences"
private const val MAX_ENTRIES = 300

// 每个订阅电台「已看到的最新一期时间」，用来判断是否有未看更新
class PodcastSeenPreferences(private val dataStore: DataStore<Preferences>) {

    companion object {
        private val KEY_SEEN = stringPreferencesKey("podcast_seen")
        private val json = Json { ignoreUnknownKeys = true }
    }

    // radioId 到已见最新一期时间戳（毫秒）。读取失败按无记录处理，最坏只是多显示「新」
    val seen: Flow<Map<Long, Long>> = dataStore.data
        .map { prefs -> decode(prefs[KEY_SEEN]) }
        .catch { e ->
            AppLogger.w(TAG, "已见更新记录读取失败", e)
            emit(emptyMap())
        }

    // 判定这批电台里哪些有未看更新，并为首次见到的电台补写基线
    suspend fun evaluate(radios: List<PodcastRadio>): Set<Long> {
        val check = PodcastSubscriptionUpdates.check(radios, seen.first())
        seedBaseline(check.baseline)
        return check.updatedRadioIds
    }

    // 只补写尚无记录的电台，作为首次看到时的基线
    suspend fun seedBaseline(baseline: Map<Long, Long>) {
        if (baseline.isEmpty()) return
        writeSafely {
            dataStore.edit { prefs ->
                val current = decode(prefs[KEY_SEEN])
                val missing = baseline.filterKeys { it !in current }
                if (missing.isEmpty()) return@edit
                prefs[KEY_SEEN] = json.encodeToString(trim(current + missing))
            }
        }
    }

    // 已见时间只增不减
    suspend fun markSeen(radioId: Long, latestProgramTimeMs: Long) {
        if (latestProgramTimeMs <= 0) return
        writeSafely {
            dataStore.edit { prefs ->
                val current = decode(prefs[KEY_SEEN])
                if ((current[radioId] ?: 0L) >= latestProgramTimeMs) return@edit
                prefs[KEY_SEEN] = json.encodeToString(trim(current + (radioId to latestProgramTimeMs)))
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_SEEN) }
    }

    // 标记类写入失败不应影响页面主流程
    private suspend fun writeSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: IOException) {
            AppLogger.w(TAG, "已见更新记录写入失败", e)
        }
    }

    // 超限时丢弃时间最旧的记录
    private fun trim(map: Map<Long, Long>): Map<Long, Long> {
        if (map.size <= MAX_ENTRIES) return map
        return map.entries.sortedByDescending { it.value }.take(MAX_ENTRIES).associate { it.key to it.value }
    }

    private fun decode(raw: String?): Map<Long, Long> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching { json.decodeFromString<Map<Long, Long>>(raw) }
            .onFailure { AppLogger.w(TAG, "已见更新记录反序列化失败", it) }
            .getOrDefault(emptyMap())
    }
}
