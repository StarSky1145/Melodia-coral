package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// SQLite 单条语句绑定参数上限 999，批量删除按此分片
private const val DELETE_CHUNK_SIZE = 500

@Dao
interface LocalTrackDao {

    @Query("SELECT * FROM local_track")
    fun observeAll(): Flow<List<LocalTrackEntity>>

    @Query("SELECT * FROM local_track")
    suspend fun getAll(): List<LocalTrackEntity>

    @Query("SELECT * FROM local_track WHERE uri = :uri")
    suspend fun getByUri(uri: String): LocalTrackEntity?

    @Query("SELECT uri FROM local_track")
    suspend fun getAllUris(): List<String>

    @Upsert
    suspend fun upsert(tracks: List<LocalTrackEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringExisting(tracks: List<LocalTrackEntity>): List<Long>

    // 用 substr 比较前缀：uri 里的 %xx 编码会被 LIKE 当成通配符
    @Query("SELECT COUNT(*) FROM local_track WHERE source = 'IMPORTED' AND substr(uri, 1, length(:prefix)) = :prefix")
    suspend fun countImportedWithPrefix(prefix: String): Int

    @Query("DELETE FROM local_track WHERE source = 'IMPORTED' AND substr(uri, 1, length(:prefix)) = :prefix")
    suspend fun deleteImportedWithPrefix(prefix: String)

    @Query("DELETE FROM local_track WHERE uri IN (:uris)")
    suspend fun deleteByUrisChunk(uris: List<String>)

    @Transaction
    suspend fun deleteByUris(uris: Collection<String>) {
        uris.chunked(DELETE_CHUNK_SIZE).forEach { deleteByUrisChunk(it) }
    }

    @Transaction
    suspend fun applySync(upserts: List<LocalTrackEntity>, deleteUris: Collection<String>) {
        if (deleteUris.isNotEmpty()) deleteByUris(deleteUris)
        if (upserts.isNotEmpty()) upsert(upserts)
    }
}
