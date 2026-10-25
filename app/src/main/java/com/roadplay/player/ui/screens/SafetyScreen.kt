package com.roadplay.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadplay.player.safety.CarConnectionMonitor
import com.roadplay.player.safety.CarMode
import com.roadplay.player.safety.ParkedVideoStatus
import com.roadplay.player.safety.ParkedVideoSupport

@Composable
fun SafetyScreen(car: CarConnectionMonitor, padding: PaddingValues, onBack: () -> Unit) {
    val mode by car.mode.collectAsStateWithLifecycle()
    val parked = ParkedVideoSupport.evaluate()

    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Android Auto & safety", style = MaterialTheme.typography.titleLarge)
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Current status", style = MaterialTheme.typography.titleSmall)
                Text(
                    when (mode) {
                        CarMode.NONE -> "Not connected to a car."
                        CarMode.PROJECTION -> "Connected to Android Auto."
                        CarMode.NATIVE -> "Running on a car's built-in system."
                    }
                )
                Text(
                    "Dashboard video: " + when (parked) {
                        is ParkedVideoStatus.Unavailable -> "unavailable"
                        ParkedVideoStatus.Available -> "available"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (parked is ParkedVideoStatus.Unavailable) {
                    Text(parked.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Info("What works in the car", "RoadPlay appears in Android Auto as an audio (media) app. You can browse your library by All videos, Favourites, Recently played and Playlists, and play the soundtrack of a video with play, pause, next and previous. No video is ever drawn on the car screen.")
        Info("Why there is no video on the dashboard", "Google's parked-video support on Android Auto is rolling out only to selected video apps, phones and vehicles, and an ordinary installed app cannot switch it on. RoadPlay does not use screen mirroring, hidden APIs or any trick to get around driving restrictions, and it never will.")
        Info("Connected does not mean parked", "Android Auto tells apps that a car is connected, not whether the car is moving. RoadPlay therefore starts audio-only whenever a car is connected and shows a reminder if you choose to show video on the phone.")
        Info("Your responsibility", "Never watch video while driving. If you use the phone's screen for video, do it only when the vehicle is safely parked, or as a passenger.")
        Info("File formats", "A file extension does not guarantee playback. Whether a video plays depends on the codecs your phone supports. H.264/AAC in MP4 is the most widely supported. If a video fails, RoadPlay says so and lets you skip to the next one.")
    }
}

@Composable
private fun Info(title: String, body: String) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}
