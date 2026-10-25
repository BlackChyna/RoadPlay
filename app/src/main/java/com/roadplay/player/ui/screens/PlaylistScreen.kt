package com.roadplay.player.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.ui.LibraryViewModel
import com.roadplay.player.ui.components.ConfirmDialog
import com.roadplay.player.ui.components.EmptyState
import com.roadplay.player.ui.components.VideoRow

@Composable
fun PlaylistScreen(
    playlistId: Long,
    vm: LibraryViewModel,
    padding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (List<VideoEntity>, VideoEntity) -> Unit,
) {
    val videos by remember(playlistId) { vm.playlistVideos(playlistId) }.collectAsState(initial = emptyList())
    var name by remember { mutableStateOf("Playlist") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(playlistId) { vm.playlistName(playlistId)?.let { name = it } }
    val playable = videos.filter { it.isAvailable }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist") }
        }
        if (playable.isNotEmpty()) {
            Button(onClick = { onPlay(playable, playable.first()) }, modifier = Modifier.padding(horizontal = 16.dp)) {
                Icon(Icons.Rounded.PlayArrow, null); Text("  Play all")
            }
        }
        if (videos.isEmpty()) {
            EmptyState(Icons.Rounded.VideoLibrary, "This playlist is empty", "Add videos from the Library tab using the ⋮ menu.")
        }
        LazyColumn {
            items(videos, key = { it.id }) { v ->
                VideoRow(
                    video = v,
                    onClick = { if (v.isAvailable) onPlay(playable, v) },
                    onToggleFavourite = { vm.toggleFavourite(v) },
                    extraMenu = { close ->
                        DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { close(); vm.removeFromPlaylist(playlistId, v.id) })
                    },
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete playlist?",
            text = "“$name” will be deleted. Your videos stay in the library.",
            confirmLabel = "Delete",
            onConfirm = { vm.deletePlaylist(playlistId); onBack() },
            onDismiss = { confirmDelete = false },
        )
    }
}
