package com.roadplay.player

import com.roadplay.player.data.library.ImportResult
import com.roadplay.player.safety.CarMode
import com.roadplay.player.safety.ParkedVideoStatus
import com.roadplay.player.safety.ParkedVideoSupport
import com.roadplay.player.safety.PlaybackSafetyPolicy
import com.roadplay.player.util.formatDuration
import com.roadplay.player.util.formatSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyAndFormatTest {
    @Test fun connectionTypesMapToModes() {
        assertEquals(CarMode.NONE, CarMode.fromConnectionType(0))
        assertEquals(CarMode.NATIVE, CarMode.fromConnectionType(1))
        assertEquals(CarMode.PROJECTION, CarMode.fromConnectionType(2))
        assertEquals(CarMode.NONE, CarMode.fromConnectionType(99))
    }

    @Test fun anyCarConnectionDefaultsToAudioOnly() {
        assertFalse(PlaybackSafetyPolicy.defaultAudioOnly(CarMode.NONE))
        assertTrue(PlaybackSafetyPolicy.defaultAudioOnly(CarMode.PROJECTION))
        assertTrue(PlaybackSafetyPolicy.defaultAudioOnly(CarMode.NATIVE))
    }

    /** Safe failure: dashboard video must stay disabled unless an official signal is wired in. */
    @Test fun parkedVideoIsUnavailableByDefault() {
        assertTrue(ParkedVideoSupport.evaluate() is ParkedVideoStatus.Unavailable)
    }

    @Test fun durationFormatting() {
        assertEquals("0:00", formatDuration(-5))
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:02:05", formatDuration(3_725_000))
    }

    @Test fun sizeFormatting() {
        assertEquals("—", formatSize(0))
        assertEquals("512 B", formatSize(512))
        assertEquals("1.5 MB", formatSize(1_572_864))
    }

    @Test fun importSummaryMessages() {
        assertEquals("Added 1 video", ImportResult(1, 0, 0).summary())
        assertEquals("Added 3 videos, 2 already in library, 1 could not be read", ImportResult(3, 2, 1).summary())
    }
}
