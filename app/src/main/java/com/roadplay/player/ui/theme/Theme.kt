package com.roadplay.player.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.roadplay.player.data.settings.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB547), onPrimary = Color(0xFF2B1B00),
    primaryContainer = Color(0xFF4A3200), onPrimaryContainer = Color(0xFFFFDDA8),
    secondary = Color(0xFF7FD6D3), onSecondary = Color(0xFF003735),
    background = Color(0xFF0E1116), onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF0E1116), onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF232A33), onSurfaceVariant = Color(0xFFB5BCC7),
    surfaceContainer = Color(0xFF161B22), surfaceContainerHigh = Color(0xFF1D232C),
    outline = Color(0xFF6B7480), error = Color(0xFFFF8A80),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDDA8), onPrimaryContainer = Color(0xFF2B1B00),
    secondary = Color(0xFF006A67), onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFBF8F3), onBackground = Color(0xFF1B1B1F),
    surface = Color(0xFFFBF8F3), onSurface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFFE8E1D7), onSurfaceVariant = Color(0xFF4B463E),
    surfaceContainer = Color(0xFFF2EDE5), surfaceContainerHigh = Color(0xFFECE6DD),
    outline = Color(0xFF7D766B), error = Color(0xFFB3261E),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun RoadPlayTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content,
    )
}
