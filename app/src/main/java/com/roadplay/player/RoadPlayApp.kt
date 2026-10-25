package com.roadplay.player

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import com.roadplay.player.data.db.RoadPlayDatabase
import com.roadplay.player.data.library.LibraryRepository
import com.roadplay.player.data.settings.SettingsRepository
import com.roadplay.player.playback.PlaybackConnection
import com.roadplay.player.safety.CarConnectionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled service locator (no DI framework, to keep the dependency list short). */
class AppContainer(private val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val database: RoadPlayDatabase by lazy { RoadPlayDatabase.build(context) }
    val settings: SettingsRepository by lazy { SettingsRepository(context) }
    val library: LibraryRepository by lazy { LibraryRepository(context, database.videoDao(), database.playlistDao()) }
    val playbackConnection: PlaybackConnection by lazy { PlaybackConnection(context) }

    /** Create only from the main thread (it observes LiveData). */
    val carMonitor: CarConnectionMonitor by lazy { CarConnectionMonitor(context) }

    /** Set by list screens right before navigating to the player (avoids huge nav arguments). */
    @Volatile var pendingRequest: PlaybackRequest? = null
}

data class PlaybackRequest(val videoIds: List<Long>, val startIndex: Int)

class RoadPlayApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    /** Coil with a video-frame decoder so local videos get real thumbnails. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(true)
            .build()
}
