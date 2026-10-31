package com.lin0721.linmusic.feature.localmusic.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "local_playlist")
data class LocalPlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long
)

// 曲目随歌单或曲库条目删除而级联清除；同一首歌在一个歌单里只出现一次
@Entity(
    tableName = "local_playlist_track",
    primaryKeys = ["playlistId", "trackUri"],
    foreignKeys = [
        ForeignKey(
            entity = LocalPlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LocalTrackEntity::class,
            parentColumns = ["uri"],
            childColumns = ["trackUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("trackUri")]
)
data class LocalPlaylistTrackEntity(
    val playlistId: Long,
    val trackUri: String,
    val position: Int
)
