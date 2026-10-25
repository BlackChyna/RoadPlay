package com.roadplay.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadplay.player.data.settings.SortField
import com.roadplay.player.data.settings.ThemeMode
import com.roadplay.player.ui.SettingsViewModel
import com.roadplay.player.ui.components.ConfirmDialog
import com.roadplay.player.ui.components.SectionHeader

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    padding: PaddingValues,
    onOpenSafety: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val s by vm.settings.collectAsStateWithLifecycle()
    var confirmHistory by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))

        SectionHeader("Appearance")
        ChipRow {
            ThemeMode.values().forEach { m ->
                FilterChip(selected = s.themeMode == m, onClick = { vm.setTheme(m) },
                    label = { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        SectionHeader("Playback")
        Text("Default speed", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        ChipRow {
            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                FilterChip(selected = s.defaultSpeed == sp, onClick = { vm.setSpeed(sp) }, label = { Text("${sp}x") })
            }
        }
        ListItem(
            headlineContent = { Text("Resume where I left off") },
            supportingContent = { Text("Continue videos from the last saved position") },
            trailingContent = { Switch(checked = s.resumePlayback, onCheckedChange = vm::setResume) },
        )

        SectionHeader("Subtitles")
        ListItem(
            headlineContent = { Text("Show subtitles automatically") },
            supportingContent = { Text("Uses your device language when a matching track exists") },
            trailingContent = { Switch(checked = s.autoSubtitles, onCheckedChange = vm::setAutoSubtitles) },
        )
        Text("Text size: ${(s.subtitleScale * 100).toInt()}%", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        Slider(value = s.subtitleScale, onValueChange = vm::setSubtitleScale, valueRange = 0.75f..1.75f, modifier = Modifier.padding(horizontal = 16.dp))

        SectionHeader("Library")
        Text("Default sorting", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        ChipRow {
            SortField.values().forEach { f ->
                FilterChip(selected = s.sortField == f, onClick = { vm.setSort(f, s.sortAscending) }, label = { Text(f.label) })
            }
        }
        ChipRow {
            FilterChip(selected = s.sortAscending, onClick = { vm.setSort(s.sortField, true) }, label = { Text("Ascending") })
            FilterChip(selected = !s.sortAscending, onClick = { vm.setSort(s.sortField, false) }, label = { Text("Descending") })
        }

        SectionHeader("Data")
        ListItem(
            headlineContent = { Text("Clear playback history") },
            supportingContent = { Text("Resets resume positions and “Recently played”") },
            modifier = Modifier.clickable { confirmHistory = true },
        )
        ListItem(
            headlineContent = { Text("Clear thumbnail cache") },
            supportingContent = { Text("Frees space. Thumbnails are regenerated on demand.") },
            modifier = Modifier.clickable { vm.clearCache { onMessage("Thumbnail cache cleared") } },
        )
        HorizontalDivider()

        SectionHeader("Android Auto & safety")
        ListItem(
            headlineContent = { Text("Android Auto information") },
            supportingContent = { Text("What works in the car, and what doesn't") },
            leadingContent = { Icon(Icons.Rounded.DirectionsCar, null) },
            modifier = Modifier.clickable(onClick = onOpenSafety),
        )

        SectionHeader("About")
        ListItem(headlineContent = { Text("Version") }, supportingContent = { Text(vm.versionName) })
        ListItem(
            headlineContent = { Text("Privacy") },
            supportingContent = { Text("Everything stays on this device. No accounts, analytics, ads or network access.") },
        )
    }

    if (confirmHistory) {
        ConfirmDialog(
            "Clear history?", "Resume positions and the “Recently played” list will be reset. Your videos, favourites and playlists are kept.",
            "Clear", onConfirm = { vm.clearHistory { onMessage("Playback history cleared") } }, onDismiss = { confirmHistory = false },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}
