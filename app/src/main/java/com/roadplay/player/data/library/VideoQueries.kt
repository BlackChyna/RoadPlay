package com.roadplay.player.data.library

import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.settings.SortField

/** Pure functions over the library list, kept free of Android types so they are unit-testable. */
object VideoQueries {
    fun sort(list: List<VideoEntity>, field: SortField, ascending: Boolean): List<VideoEntity> {
        val comparator: Comparator<VideoEntity> = when (field) {
            SortField.NAME -> compareBy<VideoEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.label() }
            SortField.DATE_ADDED -> compareBy<VideoEntity> { it.addedAt }
            SortField.DURATION -> compareBy<VideoEntity> { it.durationMs }
            SortField.SIZE -> compareBy<VideoEntity> { it.sizeBytes }
        }
        return list.sortedWith(if (ascending) comparator else comparator.reversed())
    }

    /** Case-insensitive match on file name and embedded title; every word must match. */
    fun search(list: List<VideoEntity>, query: String): List<VideoEntity> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { v ->
            val haystack = (v.displayName + " " + (v.title ?: "")).lowercase()
            words.all { haystack.contains(it) }
        }
    }

    fun continueWatching(list: List<VideoEntity>): List<VideoEntity> =
        list.filter { it.isAvailable && PositionPolicy.isInProgress(it) }
            .sortedByDescending { it.lastPlayedAt ?: 0L }

    fun recentlyPlayed(list: List<VideoEntity>, limit: Int = 20): List<VideoEntity> =
        list.filter { it.lastPlayedAt != null }.sortedByDescending { it.lastPlayedAt }.take(limit)

    fun favourites(list: List<VideoEntity>): List<VideoEntity> =
        list.filter { it.isFavourite }.sortedBy { it.label().lowercase() }

    fun recentlyAdded(list: List<VideoEntity>, limit: Int = 20): List<VideoEntity> =
        list.sortedByDescending { it.addedAt }.take(limit)
}

object PositionPolicy {
    const val MIN_RESUME_MS = 5_000L
    const val END_MARGIN_MS = 10_000L

    /** Where playback should start given a saved position. 0 = from the beginning. */
    fun resumePosition(savedMs: Long, durationMs: Long): Long {
        if (savedMs < MIN_RESUME_MS) return 0L
        if (durationMs > 0 && savedMs > durationMs - END_MARGIN_MS) return 0L
        return savedMs
    }

    fun isInProgress(v: VideoEntity): Boolean = resumePosition(v.positionMs, v.durationMs) > 0
}
