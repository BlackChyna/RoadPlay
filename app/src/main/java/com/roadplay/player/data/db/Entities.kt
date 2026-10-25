package com.roadplay.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One imported video. Only metadata and a content:// URI are stored - the video
 * file itself is never copied or deleted by this app.
 */
@Entity(tableName = "videos", indices = [Index(value = ["uri"], unique = true)])
data class VideoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val displayName: String,
    val title: String? = null,
    val durationMs: Long = 0,
    val sizeBytes: Long = 0,
    val width: Int = 0,
    val height: Int = 0,
    val hasAudio: Boolean = true,
    val addedAt: Long,
    val fileModifiedAt: Long = 0,
    val isFavourite: Boolean = false,
    val positionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val isAvailable: Boolean = true,
) {
    /** Name to show in the UI: embedded title when present, else file name. */
    fun label(): String = title?.takeIf { it.isNotBlank() } ?: displayName
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "videoId"],
    foreignKeys = [
        ForeignKey(PlaylistEntity::class, ["id"], ["playlistId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VideoEntity::class, ["id"], ["videoId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("videoId")],
)
data class PlaylistItemEntity(
    val playlistId: Long,
    val videoId: Long,
    val position: Int,
)
