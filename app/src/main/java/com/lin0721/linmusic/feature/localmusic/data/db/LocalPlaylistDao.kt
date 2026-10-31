package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalPlaylistDao {

    @Query("SELECT * FROM local_playlist ORDER BY updatedAt DESC")
    fun observePlaylists(): Flow<List<LocalPlaylistEntity>>

    @Query("SELECT * FROM local_playlist_track ORDER BY playlistId, position")
    fun observeEntries(): Flow<List<LocalPlaylistTrackEntity>>

    @Insert
    suspend fun insertPlaylist(playlist: LocalPlaylistEntity): Long

    @Query("UPDATE local_playlist SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE local_playlist SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM local_playlist WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Query("SELECT trackUri FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun trackUris(playlistId: Long): List<String>

    @Query("SELECT COALESCE(MAX(position), -1) FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<LocalPlaylistTrackEntity>)

    @Query("DELETE FROM local_playlist_track WHERE playlistId = :playlistId AND trackUri = :trackUri")
    suspend fun deleteEntry(playlistId: Long, trackUri: String)

    @Query("DELETE FROM local_playlist_track WHERE playlistId = :playlistId")
    suspend fun clearEntries(playlistId: Long)

    // 追加到末尾，已在歌单里的跳过；返回实际新增条数
    @Transaction
    suspend fun appendTracks(playlistId: Long, trackUris: List<String>, now: Long): Int {
        val existing = trackUris(playlistId).toHashSet()
        val toAdd = trackUris.distinct().filterNot { it in existing }
        if (toAdd.isEmpty()) return 0
        val start = maxPosition(playlistId) + 1
        insertEntries(toAdd.mapIndexed { index, uri -> LocalPlaylistTrackEntity(playlistId, uri, start + index) })
        touch(playlistId, now)
        return toAdd.size
    }

    @Transaction
    suspend fun removeTrack(playlistId: Long, trackUri: String, now: Long) {
        deleteEntry(playlistId, trackUri)
        touch(playlistId, now)
    }

    // 整体覆盖，顺序与移除一次写入
    @Transaction
    suspend fun replaceTracks(playlistId: Long, orderedUris: List<String>, now: Long) {
        clearEntries(playlistId)
        insertEntries(orderedUris.distinct().mapIndexed { index, uri -> LocalPlaylistTrackEntity(playlistId, uri, index) })
        touch(playlistId, now)
    }
}
