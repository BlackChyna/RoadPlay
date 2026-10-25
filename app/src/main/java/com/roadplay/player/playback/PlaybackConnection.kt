package com.roadplay.player.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.roadplay.player.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SleepState(val endsAtMs: Long? = null, val endOfVideo: Boolean = false)

/**
 * App-side handle to the PlaybackService. MainActivity connects in onStart and releases in
 * onStop, so the controller never keeps the service bound while the app is in the background
 * (playback itself continues in the service's foreground notification).
 */
class PlaybackConnection(private val context: Context) {
    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _sleep = MutableStateFlow(SleepState())
    val sleep: StateFlow<SleepState> = _sleep.asStateFlow()

    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            if (future !== f) return@addListener
            try {
                _controller.value = f.get()
            } catch (e: Exception) {
                AppLog.e("Could not connect to the playback service", e)
                future = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
        _controller.value = null
    }

    fun setSleepTimer(minutes: Int, endOfVideo: Boolean) {
        val c = _controller.value ?: return
        val args = Bundle().apply {
            putInt(RoadPlayCommands.ARG_MINUTES, minutes)
            putBoolean(RoadPlayCommands.ARG_END_OF_ITEM, endOfVideo)
        }
        c.sendCustomCommand(RoadPlayCommands.sleepTimer, args)
        _sleep.value = when {
            endOfVideo -> SleepState(endOfVideo = true)
            minutes > 0 -> SleepState(endsAtMs = System.currentTimeMillis() + minutes * 60_000L)
            else -> SleepState()
        }
    }
}
