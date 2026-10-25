package com.roadplay.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version 1. When the schema changes: bump `version`, write a Migration and add it
 * with `.addMigrations(...)` below. Never use fallbackToDestructiveMigration():
 * it would wipe the user's playlists and favourites.
 */
@Database(
    entities = [VideoEntity::class, PlaylistEntity::class, PlaylistItemEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class RoadPlayDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun playlistDao(): PlaylistDao

    companion object {
        fun build(context: Context): RoadPlayDatabase =
            Room.databaseBuilder(context.applicationContext, RoadPlayDatabase::class.java, "roadplay.db")
                .build()
    }
}
