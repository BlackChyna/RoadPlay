@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.roadplay.player.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.session.MediaController
import com.roadplay.player.PlaybackRequest
import com.roadplay.player.data.db.VideoDao
import com.roadplay.player.data.library.LibraryRepository
import com.roadplay.player.data.library.PositionPolicy
import com.roadplay.player.data.settings.AppSettings
import com.roadplay.player.data.settings.SettingsRepository
import com.roadplay.player.playback.MediaIds
import com.roadplay.player.playback.PlaybackConnection
import com.roadplay.player.playback.PlaybackErrors
import com.roadplay.player.playback.PlaybackProblem
import com.roadplay.player.playback.SleepState
import com.roadplay.player.playback.SubtitleSupport
import com.roadplay.player.playback.toMediaItem
import com.roadplay.player.safety.CarConnectionMonitor
import com.roadplay.player.safety.CarMode
import com.roadplay.player.safety.PlaybackSafetyPolicy
import com.roadplay.player.util.AppLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SubtitleOption(val index: Int, val label: String, val selected: Boolean)

data class PlayerUiState(
    val title: String = "",
    val isPlaying: Boolean = false,
    val isLoading: Boolean = true,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val audioOnly: Boolean = false,
    val carMode: CarMode = CarMode.NONE,
    val subtitleOptions: List<SubtitleOption> = emptyList(),
    val sleep: SleepState = SleepState(),
    val error: PlaybackProblem? = null,
    val message: String? = null,
)

class PlayerViewModel(
    private val context: Context,
    private val connection: PlaybackConnection,
    private val videoDao: VideoDao,
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
    private val car: CarConnectionMonitor,
    private val initialRequest: PlaybackRequest?,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()
    val controller: StateFlow<MediaController?> = connection.controller

    private var request: PlaybackRequest? = initialRequest
    private var consumed = false
    private val applyMutex = Mutex()
    private var textGroups: List<Tracks.Group> = emptyList()
    private val externalSubtitles = mutableMapOf<String, MutableList<MediaItem.SubtitleConfiguration>>()

    init {
        viewModelScope.launch {
            connection.controller.collectLatest { c -> if (c != null) observe(c) }
        }
        viewModelScope.launch {
            car.mode.collect { mode ->
                _state.update { it.copy(carMode = mode) }
                // Safer default: as soon as a car connects, stop decoding/showing video on the phone.
                if (PlaybackSafetyPolicy.defaultAudioOnly(mode)) setAudioOnly(true)
            }
        }
        viewModelScope.launch { connection.sleep.collect { s -> _state.update { it.copy(sleep = s) } } }
    }

    // ---- Starting playback --------------------------------------------------------------------

    private suspend fun observe(c: MediaController) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { refresh(c) }
            override fun onPlayerError(error: PlaybackException) { handleError(c, error) }
        }
        c.addListener(listener)
        try {
            applyRequest(c)
            while (true) {
                refresh(c)
                delay(500)
            }
        } finally {
            c.removeListener(listener)
        }
    }

    private suspend fun applyRequest(c: MediaController) = applyMutex.withLock {
        val req = request ?: return@withLock
        if (consumed && c.mediaItemCount > 0) return@withLock

        val byId = videoDao.getByIds(req.videoIds).associateBy { it.id }
        val ordered = req.videoIds.mapNotNull { byId[it] }.filter { it.isAvailable }
        if (ordered.isEmpty()) {
            _state.update { it.copy(isLoading = false, error = PlaybackProblem("No playable videos. They may have been moved or removed.", false)) }
            consumed = true
            return@withLock
        }
        val tapped = req.videoIds.getOrNull(req.startIndex)
        val startIndex = ordered.indexOfFirst { it.id == tapped }.coerceAtLeast(0)
        val settings = settingsRepo.settings.first()

        val queueIds = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        val wantedIds = ordered.map { MediaIds.video(it.id) }
        if (queueIds == wantedIds && c.currentMediaItemIndex == startIndex) {
            if (!c.isPlaying) c.play() // same queue already loaded: just continue
        } else {
            val items = ordered.map { it.toMediaItem() }
            val video = ordered[startIndex]
            val startPos = if (settings.resumePlayback) PositionPolicy.resumePosition(video.positionMs, video.durationMs) else 0L
            c.setMediaItems(items, startIndex, startPos)
            c.setPlaybackSpeed(settings.defaultSpeed)
            applyTextPreferences(c, settings)
            c.prepare()
            c.play()
        }
        setAudioOnly(PlaybackSafetyPolicy.defaultAudioOnly(car.mode.value))
        consumed = true
    }

    fun start(request: PlaybackRequest?) {
        this.request = request ?: this.request
        consumed = false
        viewModelScope.launch { connection.controller.value?.let { applyRequest(it) } }
    }

    private fun applyTextPreferences(c: MediaController, s: AppSettings) {
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !s.autoSubtitles)
            .setPreferredTextLanguage(java.util.Locale.getDefault().language)
            .setSelectUndeterminedTextLanguage(true)
            .build()
    }

    // ---- State refresh --------------------------------------------------------------------------

    private fun refresh(c: MediaController) {
        val groups = c.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        textGroups = groups
        val options = groups.mapIndexed { i, g ->
            val f = g.mediaTrackGroup.getFormat(0)
            SubtitleOption(i, f.label ?: f.language ?: "Subtitle ${i + 1}", g.isSelected)
        }
        val audioOnly = C.TRACK_TYPE_VIDEO in c.trackSelectionParameters.disabledTrackTypes
        _state.update {
            it.copy(
                title = c.mediaMetadata.title?.toString() ?: it.title,
                isPlaying = c.isPlaying,
                isLoading = c.playbackState == Player.STATE_BUFFERING || c.playbackState == Player.STATE_IDLE && it.error == null && c.mediaItemCount == 0,
                positionMs = c.currentPosition,
                durationMs = if (c.duration == C.TIME_UNSET) 0L else c.duration,
                speed = c.playbackParameters.speed,
                hasNext = c.hasNextMediaItem(),
                hasPrevious = c.hasPreviousMediaItem(),
                audioOnly = audioOnly,
                subtitleOptions = options,
            )
        }
    }

    private fun handleError(c: MediaController, error: PlaybackException) {
        AppLog.w("Playback error ${error.errorCodeName}", error)
        val problem = PlaybackErrors.describe(error.errorCode)
        _state.update { it.copy(error = problem, isLoading = false) }
        if (problem.markUnavailable) {
            c.currentMediaItem?.mediaId?.let(MediaIds::parseVideo)?.let { id ->
                viewModelScope.launch { library.markUnavailable(id) }
            }
        }
    }

    // ---- User actions ------------------------------------------------------------------------------

    private fun withController(block: (MediaController) -> Unit) { connection.controller.value?.let(block) }

    fun togglePlay() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun pause() = withController { it.pause() }
    fun seekBy(deltaMs: Long) = withController {
        val target = (it.currentPosition + deltaMs).coerceAtLeast(0)
        val d = it.duration
        it.seekTo(if (d != C.TIME_UNSET) target.coerceAtMost(d) else target)
    }
    fun seekTo(ms: Long) = withController { it.seekTo(ms.coerceAtLeast(0)) }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController {
        // Like most players: restart the current video first, go back on a second press.
        if (it.currentPosition > 3_000 || !it.hasPreviousMediaItem()) it.seekTo(0) else it.seekToPreviousMediaItem()
    }
    fun setSpeed(speed: Float) = withController { it.setPlaybackSpeed(speed) }

    fun retry() = withController {
        _state.update { s -> s.copy(error = null, isLoading = true) }
        it.prepare(); it.play()
    }
    fun dismissError() { _state.update { it.copy(error = null) } }
    fun dismissMessage() { _state.update { it.copy(message = null) } }

    fun setAudioOnly(enabled: Boolean) = withController { c ->
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, enabled)
            .build()
    }

    fun setSleepTimer(minutes: Int) = connection.setSleepTimer(minutes, false)
    fun setSleepEndOfVideo() = connection.setSleepTimer(0, true)
    fun cancelSleepTimer() = connection.setSleepTimer(0, false)

    // ---- Subtitles ----------------------------------------------------------------------------------

    fun selectSubtitle(index: Int?) = withController { c ->
        val params = c.trackSelectionParameters.buildUpon()
        if (index == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val group = textGroups.getOrNull(index) ?: return@withController
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        }
        c.trackSelectionParameters = params.build()
    }

    fun addExternalSubtitle(uri: Uri) {
        val c = connection.controller.value ?: return
        val name = displayName(uri)
        val mime = SubtitleSupport.mimeFor(name)
        if (mime == null) {
            _state.update { it.copy(message = "Unsupported subtitle format. Use .srt, .vtt, .ass/.ssa or .ttml") }
            return
        }
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent_FLAG_READ)
        } catch (_: Exception) { /* transient grant is enough for this session */ }

        val mediaId = c.currentMediaItem?.mediaId ?: return
        val videoId = MediaIds.parseVideo(mediaId) ?: return
        viewModelScope.launch {
            val video = videoDao.get(videoId) ?: return@launch
            val sub = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(mime)
                .setLabel(name.substringBeforeLast('.'))
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val list = externalSubtitles.getOrPut(mediaId) { mutableListOf() }
            list += sub
            val rebuilt = video.toMediaItem().buildUpon().setSubtitleConfigurations(list.toList()).build()
            val index = c.currentMediaItemIndex
            val position = c.currentPosition
            c.replaceMediaItem(index, rebuilt)
            c.seekTo(index, position)
            c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
            _state.update { it.copy(message = "Subtitles loaded: $name") }
        }
    }

    private fun displayName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && cur.moveToFirst()) return cur.getString(i)
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment ?: "subtitle"
    }

    private companion object {
        const val Intent_FLAG_READ = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
    }
}
