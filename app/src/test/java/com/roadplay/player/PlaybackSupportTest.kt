package com.roadplay.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.playback.MediaIds
import com.roadplay.player.playback.PlaybackErrors
import com.roadplay.player.playback.SubtitleSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSupportTest {
    @Test fun mediaIdsRoundTrip() {
        assertEquals(42L, MediaIds.parseVideo(MediaIds.video(42)))
        assertEquals(7L, MediaIds.parsePlaylist(MediaIds.playlist(7)))
        assertNull(MediaIds.parseVideo("playlist:7"))
        assertNull(MediaIds.parseVideo("video:abc"))
        assertNull(MediaIds.parsePlaylist(MediaIds.ROOT))
    }

    @Test fun subtitleMimeTypes() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, SubtitleSupport.mimeFor("Movie.EN.srt"))
        assertEquals(MimeTypes.TEXT_VTT, SubtitleSupport.mimeFor("a.vtt"))
        assertEquals(MimeTypes.TEXT_SSA, SubtitleSupport.mimeFor("a.ass"))
        assertNull(SubtitleSupport.mimeFor("a.docx"))
        assertNull(SubtitleSupport.mimeFor("noextension"))
    }

    @Test fun missingFileAndRevokedPermissionAreFlagged() {
        val missing = PlaybackErrors.describe(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        assertTrue(missing.markUnavailable)
        assertTrue(missing.message.contains("moved or deleted"))
        assertTrue(PlaybackErrors.describe(PlaybackException.ERROR_CODE_IO_NO_PERMISSION).markUnavailable)
    }

    @Test fun codecAndCorruptErrorsAreExplainedNotFlagged() {
        val codec = PlaybackErrors.describe(PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        assertFalse(codec.markUnavailable)
        assertTrue(codec.message.contains("decode"))
        assertTrue(PlaybackErrors.describe(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED).message.contains("corrupt"))
        assertTrue(PlaybackErrors.describe(9999).message.contains("9999"))
    }

    @Test fun entityLabelPrefersTitleThenFileName() {
        val base = VideoEntity(uri = "u", displayName = "file.mp4", addedAt = 0)
        assertEquals("file.mp4", base.label())
        assertEquals("file.mp4", base.copy(title = "  ").label())
        assertEquals("Nice", base.copy(title = "Nice").label())
    }
}
