package com.roadplay.player

import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.library.PositionPolicy
import com.roadplay.player.data.library.VideoQueries
import com.roadplay.player.data.settings.SortField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoQueriesTest {
    private fun v(id: Long, name: String, added: Long = id, dur: Long = 60_000, size: Long = 1000, pos: Long = 0, played: Long? = null, fav: Boolean = false, title: String? = null, avail: Boolean = true) =
        VideoEntity(id = id, uri = "content://x/$id", displayName = name, title = title, durationMs = dur, sizeBytes = size,
            addedAt = added, positionMs = pos, lastPlayedAt = played, isFavourite = fav, isAvailable = avail)

    private val list = listOf(
        v(1, "banana.mp4", added = 3, dur = 100, size = 30),
        v(2, "Apple.mp4", added = 1, dur = 300, size = 10),
        v(3, "cherry.mp4", added = 2, dur = 200, size = 20, title = "Holiday in Accra"),
    )

    @Test fun sortByNameIsCaseInsensitive() =
        assertEquals(listOf(2L, 1L, 3L), VideoQueries.sort(list, SortField.NAME, true).map { it.id })

    @Test fun sortByDateDescending() =
        assertEquals(listOf(1L, 3L, 2L), VideoQueries.sort(list, SortField.DATE_ADDED, false).map { it.id })

    @Test fun sortByDurationAndSize() {
        assertEquals(listOf(2L, 3L, 1L), VideoQueries.sort(list, SortField.DURATION, false).map { it.id })
        assertEquals(listOf(2L, 3L, 1L), VideoQueries.sort(list, SortField.SIZE, true).map { it.id })
    }

    @Test fun searchMatchesFileNameAndTitle() {
        assertEquals(listOf(2L), VideoQueries.search(list, "apple").map { it.id })
        assertEquals(listOf(3L), VideoQueries.search(list, "holiday accra").map { it.id })
        assertEquals(3, VideoQueries.search(list, "  ").size)
        assertTrue(VideoQueries.search(list, "zzz").isEmpty())
    }

    @Test fun continueWatchingNeedsMeaningfulProgress() {
        val items = listOf(
            v(1, "a", pos = 30_000, played = 10, dur = 600_000),
            v(2, "b", pos = 2_000, played = 20, dur = 600_000),     // too early
            v(3, "c", pos = 595_000, played = 30, dur = 600_000),   // basically finished
            v(4, "d", pos = 50_000, played = 40, dur = 600_000, avail = false),
        )
        assertEquals(listOf(1L), VideoQueries.continueWatching(items).map { it.id })
    }

    @Test fun favouritesAndRecents() {
        val items = listOf(v(1, "b", fav = true), v(2, "a", fav = true), v(3, "c", played = 5), v(4, "d", played = 9))
        assertEquals(listOf(2L, 1L), VideoQueries.favourites(items).map { it.id })
        assertEquals(listOf(4L, 3L), VideoQueries.recentlyPlayed(items).map { it.id })
        assertEquals(4L, VideoQueries.recentlyAdded(items).first().id)
    }

    @Test fun resumePositionRules() {
        assertEquals(0L, PositionPolicy.resumePosition(1_000, 100_000))
        assertEquals(50_000L, PositionPolicy.resumePosition(50_000, 100_000))
        assertEquals(0L, PositionPolicy.resumePosition(95_000, 100_000))
        assertEquals(50_000L, PositionPolicy.resumePosition(50_000, 0)) // unknown duration
        assertFalse(PositionPolicy.isInProgress(v(1, "x", pos = 0)))
    }
}
