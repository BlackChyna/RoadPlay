@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.roadplay.player.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.roadplay.player.safety.CarMode
import com.roadplay.player.ui.PlayerViewModel
import com.roadplay.player.util.formatDuration
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun PlayerScreen(vm: PlayerViewModel, subtitleScale: Float, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val controller by vm.controller.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var controlsVisible by remember { mutableStateOf(true) }
    var interaction by remember { mutableIntStateOf(0) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var landscape by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf<String?>(null) }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var volumeFraction by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
    }
    var brightness by remember { mutableFloatStateOf(0.5f) }

    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addExternalSubtitle(uri)
    }

    // Fullscreen immersive mode while this screen is visible.
    DisposableEffect(activity) {
        val window = activity?.window
        val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets?.hide(WindowInsetsCompat.Type.systemBars())
        val current = window?.attributes?.screenBrightness ?: -1f
        brightness = if (current >= 0f) current else 0.5f
        onDispose {
            insets?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }
    // Keep the screen awake only while video is actually playing.
    DisposableEffect(state.isPlaying, state.audioOnly) {
        val w = activity?.window
        if (state.isPlaying && !state.audioOnly) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { }
    }
    LaunchedEffect(controlsVisible, state.isPlaying, interaction) {
        if (controlsVisible && state.isPlaying) { delay(3_500); controlsVisible = false }
    }
    LaunchedEffect(hud) { if (hud != null) { delay(900); hud = null } }

    fun leave() { if (!state.audioOnly) vm.pause(); onBack() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (!state.audioOnly) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = false
                    }
                },
                update = { view ->
                    view.player = controller
                    view.resizeMode = resizeMode
                    view.subtitleView?.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * subtitleScale)
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.Headphones, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp))
                Text("Audio only", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text("Video is hidden. Sound keeps playing.", color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
            }
        }

        // Gesture layer: tap = toggle controls, double-tap = seek, vertical drag = volume (right) / brightness (left)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible; interaction++ },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) { vm.seekBy(-10_000); hud = "−10 s" } else { vm.seekBy(10_000); hud = "+10 s" }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dy ->
                        change.consume()
                        val delta = -dy / size.height
                        if (change.position.x > size.width / 2f) {
                            volumeFraction = (volumeFraction + delta).coerceIn(0f, 1f)
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeFraction * max).roundToInt(), 0)
                            hud = "Volume ${(volumeFraction * 100).roundToInt()}%"
                        } else {
                            brightness = (brightness + delta).coerceIn(0.02f, 1f)
                            activity?.window?.let { w -> val lp = w.attributes; lp.screenBrightness = brightness; w.attributes = lp }
                            hud = "Brightness ${(brightness * 100).roundToInt()}%"
                        }
                    }
                },
        )

        if (state.isLoading && state.error == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.primary)
        }
        hud?.let {
            Text(
                it, color = Color.White,
                modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.65f), MaterialTheme.shapes.small).padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // Safety banner when connected to a car (connection != parked; see Safety screen).
        if (state.carMode != CarMode.NONE) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DirectionsCar, null)
                    Text(
                        "  Android Auto is connected. Watch video only when the vehicle is safely parked, and never as the driver while driving.",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.setAudioOnly(!state.audioOnly) }) { Text(if (state.audioOnly) "Show video" else "Audio only") }
                }
            }
        }

        state.error?.let { problem ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Can't play this video", style = MaterialTheme.typography.titleMedium)
                    Text(problem.message, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!problem.markUnavailable) Button(onClick = vm::retry) { Text("Retry") }
                        if (state.hasNext) TextButton(onClick = { vm.dismissError(); vm.next() }) { Text("Skip to next") }
                        TextButton(onClick = ::leave) { Text("Back") }
                    }
                }
            }
        }

        state.message?.let { msg ->
            LaunchedEffect(msg) { delay(2_500); vm.dismissMessage() }
            Text(
                msg, color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 140.dp)
                    .background(Color.Black.copy(alpha = 0.75f), MaterialTheme.shapes.small).padding(12.dp),
            )
        }

        AnimatedVisibility(controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                // ---- Top bar ----
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).statusBarsPadding().padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = ::leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                        Text(state.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.setAudioOnly(!state.audioOnly); interaction++ }) {
                            Icon(if (state.audioOnly) Icons.Rounded.Videocam else Icons.Rounded.Headphones,
                                if (state.audioOnly) "Show video" else "Audio only", tint = Color.White)
                        }
                        MenuIcon(Icons.Rounded.Subtitles, "Subtitles") { close ->
                            DropdownMenuItem(
                                text = { Text("Off") },
                                trailingIcon = { if (state.subtitleOptions.none { it.selected }) Icon(Icons.Rounded.Check, null) },
                                onClick = { vm.selectSubtitle(null); close() },
                            )
                            state.subtitleOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt.label) },
                                    trailingIcon = { if (opt.selected) Icon(Icons.Rounded.Check, null) },
                                    onClick = { vm.selectSubtitle(opt.index); close() },
                                )
                            }
                            DropdownMenuItem(text = { Text("Load subtitle file…") }, onClick = { close(); subtitlePicker.launch(arrayOf("*/*")) })
                        }
                        MenuIcon(Icons.Rounded.AspectRatio, "Aspect ratio") { close ->
                            listOf(
                                "Fit (original ratio)" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
                                "Fill (stretch)" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
                                "Crop (zoom)" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                            ).forEach { (label, mode) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    trailingIcon = { if (resizeMode == mode) Icon(Icons.Rounded.Check, null) },
                                    onClick = { resizeMode = mode; close() },
                                )
                            }
                        }
                        MenuText("${state.speed}x", "Playback speed") { close ->
                            SPEEDS.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text("${s}x") },
                                    trailingIcon = { if (state.speed == s) Icon(Icons.Rounded.Check, null) },
                                    onClick = { vm.setSpeed(s); close() },
                                )
                            }
                        }
                        MenuIcon(Icons.Rounded.Bedtime, "Sleep timer") { close ->
                            val sleepLabel = when {
                                state.sleep.endOfVideo -> "Sleep: end of video"
                                state.sleep.endsAtMs != null && state.sleep.endsAtMs!! > System.currentTimeMillis() ->
                                    "Sleep in ${((state.sleep.endsAtMs!! - System.currentTimeMillis()) / 60_000) + 1} min"
                                else -> "Sleep timer off"
                            }
                            Text(sleepLabel, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium)
                            listOf(15, 30, 45, 60).forEach { m ->
                                DropdownMenuItem(text = { Text("$m minutes") }, onClick = { vm.setSleepTimer(m); close() })
                            }
                            DropdownMenuItem(text = { Text("End of this video") }, onClick = { vm.setSleepEndOfVideo(); close() })
                            DropdownMenuItem(text = { Text("Turn off") }, onClick = { vm.cancelSleepTimer(); close() })
                        }
                        IconButton(onClick = {
                            landscape = !landscape
                            activity?.requestedOrientation =
                                if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        }) { Icon(Icons.Rounded.ScreenRotation, "Rotate screen", tint = Color.White) }
                    }
                }

                // ---- Bottom bar ----
                Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)).navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val duration = state.durationMs
                    val progress = if (duration > 0) (state.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    val shownFraction = dragFraction ?: progress
                    Slider(
                        value = shownFraction,
                        onValueChange = { dragFraction = it; interaction++ },
                        onValueChangeFinished = {
                            dragFraction?.let { vm.seekTo((it * duration).toLong()) }
                            dragFraction = null
                        },
                        enabled = duration > 0,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatDuration((shownFraction * duration).toLong()), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Text("−" + formatDuration((duration - (shownFraction * duration).toLong()).coerceAtLeast(0)), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.previous() }) { Icon(Icons.Rounded.SkipPrevious, "Previous", tint = Color.White, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = { vm.seekBy(-10_000) }) { Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = { vm.togglePlay(); interaction++ }, modifier = Modifier.size(64.dp)) {
                            Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        }
                        IconButton(onClick = { vm.seekBy(10_000) }) { Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = { vm.next() }, enabled = state.hasNext) { Icon(Icons.Rounded.SkipNext, "Next", tint = if (state.hasNext) Color.White else Color.Gray, modifier = Modifier.size(32.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuIcon(icon: ImageVector, description: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(icon, description, tint = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}

@Composable
private fun MenuText(label: String, description: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.(close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text(label, color = Color.White) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { content { open = false } }
    }
}
