package com.roadplay.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos")
    fun observeAll(): Flow<List<VideoEntity>>

    @Query("SELECT * FROM videos")
    suspend fun getAll(): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun get(id: Long): VideoEntity?

    @Query("SELECT * FROM videos WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<VideoEntity>

    @Query("SELECT * FROM videos WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoEntity?

    @Query(
        "SELECT * FROM videos WHERE lastPlayedAt IS NOT NULL AND isAvailable = 1 AND hasAudio = 1 " +
            "ORDER BY lastPlayedAt DESC LIMIT 1"
    )
    suspend fun lastPlayedWithAudio(): VideoEntity?

    /** Returns the new row id, or -1 if a video with this URI already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(video: VideoEntity): Long

    @Update
    suspend fun update(video: VideoEntity)

    @Query("DELETE FROM videos WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE videos SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE videos SET positionMs = :positionMs, lastPlayedAt = :now WHERE id = :id")
    suspend fun savePosition(id: Long, positionMs: Long, now: Long)

    @Query("UPDATE videos SET isAvailable = :available WHERE id = :id")
    suspend fun setAvailable(id: Long, available: Boolean)

    @Query("UPDATE videos SET positionMs = 0, lastPlayedAt = NULL")
    suspend fun clearHistory()
}

@Dao
abstract class PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    abstract fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist_items")
    abstract fun observeAllItems(): Flow<List<PlaylistItemEntity>>

    @Query(
        "SELECT v.* FROM videos v INNER JOIN playlist_items pi ON pi.videoId = v.id " +
            "WHERE pi.playlistId = :playlistId ORDER BY pi.position ASC"
    )
    abstract fun observeVideos(playlistId: Long): Flow<List<VideoEntity>>

    @Query(
        "SELECT v.* FROM videos v INNER JOIN playlist_items pi ON pi.videoId = v.id " +
            "WHERE pi.playlistId = :playlistId ORDER BY pi.position ASC"
    )
    abstract suspend fun videosOf(playlistId: Long): List<VideoEntity>

    @Query("SELECT * FROM playlists WHERE id = :id")
    abstract suspend fun getPlaylist(id: Long): PlaylistEntity?

    @Insert
    abstract suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :id")
    abstract suspend fun deletePlaylist(id: Long)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    abstract suspend fun maxPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertItem(item: PlaylistItemEntity)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND videoId = :videoId")
    abstract suspend fun removeItem(playlistId: Long, videoId: Long)

    @Transaction
    open suspend fun addVideos(playlistId: Long, videoIds: List<Long>) {
        var position = maxPosition(playlistId) + 1
        for (id in videoIds) insertItem(PlaylistItemEntity(playlistId, id, position++))
    }
}
