package com.roadplay.player.safety

import android.content.Context
import androidx.car.app.connection.CarConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** How this phone is currently connected to a car, as reported by Android Auto. */
enum class CarMode {
    NONE, PROJECTION, NATIVE;

    companion object {
        fun fromConnectionType(type: Int): CarMode = when (type) {
            CarConnection.CONNECTION_TYPE_PROJECTION -> PROJECTION
            CarConnection.CONNECTION_TYPE_NATIVE -> NATIVE
            else -> NONE
        }
    }
}

/**
 * Observes the official androidx.car.app CarConnection API. NOTE: "connected" is NOT
 * "parked" and tells us nothing about vehicle speed. It is used only to choose safer
 * defaults (audio-only) and to show information to the user.
 * Must be created on the main thread.
 */
class CarConnectionMonitor(context: Context) {
    private val _mode = MutableStateFlow(CarMode.NONE)
    val mode: StateFlow<CarMode> = _mode

    init {
        CarConnection(context.applicationContext).type.observeForever {
            _mode.value = CarMode.fromConnectionType(it)
        }
    }
}

/** Status of OFFICIAL Android Auto parked-video support for this app. */
sealed interface ParkedVideoStatus {
    data class Unavailable(val reason: String) : ParkedVideoStatus

    /** Reserved: only to be returned once an official, documented pathway exists. */
    data object Available : ParkedVideoStatus
}

object ParkedVideoSupport {
    /**
     * Always Unavailable today. Do not make this return Available because a heuristic
     * (connection type, speed, screen state) "looks parked" - only a platform-provided,
     * documented signal may enable dashboard video. See docs/ANDROID_AUTO_COMPATIBILITY.md.
     */
    fun evaluate(): ParkedVideoStatus = ParkedVideoStatus.Unavailable(
        "Android Auto dashboard video for non-streaming local-file apps is not publicly available. " +
            "RoadPlay provides audio-only playback in the car."
    )
}

object PlaybackSafetyPolicy {
    /** When any car connection exists, phone playback starts audio-only until the user opts in. */
    fun defaultAudioOnly(mode: CarMode): Boolean = mode != CarMode.NONE
}
