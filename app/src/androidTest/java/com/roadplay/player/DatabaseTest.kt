package com.roadplay.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.roadplay.player.data.db.PlaylistEntity
import com.roadplay.player.data.db.RoadPlayDatabase
import com.roadplay.player.data.db.VideoEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Room behaviour on a real device/emulator: dedupe, favourites, history, playlists, cascade deletes. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: RoadPlayDatabase

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, RoadPlayDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() { db.close() }

    private fun video(n: Int) = VideoEntity(uri = "content://test/$n", displayName = "v$n.mp4", addedAt = n.toLong())

    @Test fun duplicateUrisAreIgnored() = runBlocking {
        val dao = db.videoDao()
        assertTrue(dao.insert(video(1)) > 0)
        assertEquals(-1L, dao.insert(video(1)))
        assertEquals(1, dao.getAll().size)
    }

    @Test fun positionFavouriteAndHistory() = runBlocking {
        val dao = db.videoDao()
        val id = dao.insert(video(1))
        dao.setFavourite(id, true)
        dao.savePosition(id, 42_000, now = 1000)
        val saved = dao.get(id)!!
        assertTrue(saved.isFavourite); assertEquals(42_000L, saved.positionMs); assertEquals(1000L, saved.lastPlayedAt)
        dao.clearHistory()
        val cleared = dao.get(id)!!
        assertEquals(0L, cleared.positionMs); assertNull(cleared.lastPlayedAt); assertTrue(cleared.isFavourite)
    }

    @Test fun playlistKeepsOrderAndCascades() = runBlocking {
        val videos = db.videoDao(); val lists = db.playlistDao()
        val ids = (1..3).map { videos.insert(video(it)) }
        val pid = lists.insertPlaylist(PlaylistEntity(name = "Trip", createdAt = 1))
        lists.addVideos(pid, listOf(ids[2], ids[0], ids[1]))
        assertEquals(listOf(ids[2], ids[0], ids[1]), lists.videosOf(pid).map { it.id })

        lists.removeItem(pid, ids[0])
        assertEquals(2, lists.observeVideos(pid).first().size)

        videos.delete(ids[2]) // removing a video must not break the playlist
        assertEquals(listOf(ids[1]), lists.videosOf(pid).map { it.id })

        lists.deletePlaylist(pid)
        assertEquals(0, lists.observeAllItems().first().size)
        assertEquals(2, videos.getAll().size) // videos survive playlist deletion
    }
}
