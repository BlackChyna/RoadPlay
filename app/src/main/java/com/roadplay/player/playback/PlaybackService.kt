@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.roadplay.player.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.roadplay.player.AppContainer
import com.roadplay.player.MainActivity
import com.roadplay.player.RoadPlayApp
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.library.PositionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Custom session commands shared between the app UI and the service. */
object RoadPlayCommands {
    const val SLEEP_ACTION = "com.roadplay.player.SLEEP_TIMER"
    const val ARG_MINUTES = "minutes"
    const val ARG_END_OF_ITEM = "end_of_item"
    val sleepTimer = SessionCommand(SLEEP_ACTION, Bundle.EMPTY)
}

/**
 * Owns the ExoPlayer and exposes it through a MediaLibrarySession. Both the phone UI
 * (through a MediaController) and Android Auto / Bluetooth / notifications talk to
 * this one session, so playback state is always consistent.
 *
 * Android Auto never receives video frames: playback started by an Auto/Automotive
 * controller disables the video track entirely (audio-only).
 */
class PlaybackService : MediaLibraryService() {

    private var librarySession: MediaLibrarySession? = null
    private lateinit var exo: ExoPlayer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var sleepJob: Job? = null
    private val container: AppContainer get() = (application as RoadPlayApp).container

    override fun onCreate() {
        super.onCreate()
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug / BT drops
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        exo.addListener(PositionListener())

        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        librarySession = MediaLibrarySession.Builder(this, exo, LibraryCallback())
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = librarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing only if the user is actually listening; otherwise free resources.
        if (!exo.playWhenReady || exo.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        saveCurrentPosition()
        positionJob?.cancel()
        sleepJob?.cancel()
        librarySession?.release()
        librarySession = null
        exo.release()
        scope.cancel()
        super.onDestroy()
    }

    // ---- Position persistence (survives process death) ----------------------------------

    private fun persist(mediaId: String?, positionMs: Long) {
        val id = mediaId?.let(MediaIds::parseVideo) ?: return
        val now = System.currentTimeMillis()
        val dao = container.database.videoDao()
        // App scope, not service scope: the write must finish even while the service is dying.
        container.appScope.launch(Dispatchers.IO) { dao.savePosition(id, positionMs.coerceAtLeast(0), now) }
    }

    private fun saveCurrentPosition() {
        persist(exo.currentMediaItem?.mediaId, exo.currentPosition)
    }

    private inner class PositionListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            positionJob?.cancel()
            if (isPlaying) {
                positionJob = scope.launch {
                    while (isActive) { delay(5_000); saveCurrentPosition() }
                }
            } else {
                saveCurrentPosition()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            val finished = reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION
            if (finished || oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                persist(oldPosition.mediaItem?.mediaId, if (finished) 0L else oldPosition.positionMs)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                exo.setPauseAtEndOfMediaItems(false) // "end of video" sleep timer has fired
            }
        }
    }

    // ---- Sleep timer --------------------------------------------------------------------

    private fun setSleepTimer(minutes: Int, endOfItem: Boolean) {
        sleepJob?.cancel()
        exo.setPauseAtEndOfMediaItems(false)
        when {
            endOfItem -> exo.setPauseAtEndOfMediaItems(true)
            minutes > 0 -> sleepJob = scope.launch {
                delay(minutes * 60_000L)
                exo.pause()
            }
        }
    }

    // ---- Browse tree / Android Auto ---------------------------------------------------------

    private fun applyVideoPolicy(audioOnly: Boolean) {
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    private fun isCarController(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
        session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)

    private fun eligible(list: List<VideoEntity>) = list.filter { it.isAvailable && it.hasAudio }

    private suspend fun childrenOf(parentId: String): List<MediaItem>? {
        val videos = container.database.videoDao()
        val playlists = container.database.playlistDao()
        return when {
            parentId == MediaIds.ROOT -> listOf(
                folderItem(MediaIds.ALL, "All videos (audio only)"),
                folderItem(MediaIds.FAVOURITES, "Favourites"),
                folderItem(MediaIds.RECENT, "Recently played"),
                folderItem(MediaIds.PLAYLISTS, "Playlists"),
            )
            parentId == MediaIds.ALL ->
                eligible(videos.getAll()).sortedBy { it.label().lowercase() }.map { it.toMediaItem() }
            parentId == MediaIds.FAVOURITES ->
                eligible(videos.getAll()).filter { it.isFavourite }.sortedBy { it.label().lowercase() }.map { it.toMediaItem() }
            parentId == MediaIds.RECENT || parentId == MediaIds.RECENT_ROOT ->
                eligible(videos.getAll()).filter { it.lastPlayedAt != null }
                    .sortedByDescending { it.lastPlayedAt }.take(20).map { it.toMediaItem() }
            parentId == MediaIds.PLAYLISTS ->
                playlists.observePlaylists().first().map { folderItem(MediaIds.playlist(it.id), it.name) }
            else -> MediaIds.parsePlaylist(parentId)?.let { pid ->
                eligible(playlists.videosOf(pid)).map { it.toMediaItem() }
            }
        }
    }

    private suspend fun resolve(item: MediaItem): MediaItem {
        if (item.localConfiguration != null) return item // already carries a URI (phone UI)
        val id = MediaIds.parseVideo(item.mediaId) ?: return item
        return container.database.videoDao().get(id)?.toMediaItem() ?: item
    }

    private fun <T> List<T>.paged(page: Int, pageSize: Int): List<T> {
        val from = (page.toLong() * pageSize).coerceAtMost(size.toLong()).toInt()
        return drop(from).take(pageSize.coerceAtLeast(0))
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(RoadPlayCommands.sleepTimer)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == RoadPlayCommands.SLEEP_ACTION) {
                setSleepTimer(
                    args.getInt(RoadPlayCommands.ARG_MINUTES, 0),
                    args.getBoolean(RoadPlayCommands.ARG_END_OF_ITEM, false),
                )
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val id = if (params?.isRecent == true) MediaIds.RECENT_ROOT else MediaIds.ROOT
            return Futures.immediateFuture(LibraryResult.ofItem(folderItem(id, "RoadPlay"), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val item: MediaItem? = when {
                mediaId == MediaIds.ROOT || mediaId == MediaIds.RECENT_ROOT -> folderItem(mediaId, "RoadPlay")
                mediaId == MediaIds.ALL -> folderItem(mediaId, "All videos (audio only)")
                mediaId == MediaIds.FAVOURITES -> folderItem(mediaId, "Favourites")
                mediaId == MediaIds.RECENT -> folderItem(mediaId, "Recently played")
                mediaId == MediaIds.PLAYLISTS -> folderItem(mediaId, "Playlists")
                MediaIds.parsePlaylist(mediaId) != null ->
                    container.database.playlistDao().getPlaylist(MediaIds.parsePlaylist(mediaId)!!)
                        ?.let { folderItem(mediaId, it.name) }
                MediaIds.parseVideo(mediaId) != null ->
                    container.database.videoDao().get(MediaIds.parseVideo(mediaId)!!)?.toMediaItem()
                else -> null
            }
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val children = childrenOf(parentId)
            if (children == null) LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            else LibraryResult.ofItemList(ImmutableList.copyOf(children.paged(page, pageSize)), params)
        }

        /** Android Auto sends items that only carry a mediaId; turn them into playable items. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            applyVideoPolicy(audioOnly = isCarController(mediaSession, controller))
            mediaItems.map { resolve(it) }.toMutableList()
        }

        /** Lets Bluetooth/Auto "play" resume the last video after the process was killed. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            applyVideoPolicy(audioOnly = isCarController(mediaSession, controller))
            val last = container.database.videoDao().lastPlayedWithAudio()
                ?: throw UnsupportedOperationException("Nothing to resume")
            MediaSession.MediaItemsWithStartPosition(
                listOf(last.toMediaItem()), 0, PositionPolicy.resumePosition(last.positionMs, last.durationMs),
            )
        }
    }
}
