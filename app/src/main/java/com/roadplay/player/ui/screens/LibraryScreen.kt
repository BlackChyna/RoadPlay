package com.roadplay.player.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.settings.SortField
import com.roadplay.player.ui.LibraryViewModel
import com.roadplay.player.ui.components.AddToPlaylistDialog
import com.roadplay.player.ui.components.ConfirmDialog
import com.roadplay.player.ui.components.EmptyState
import com.roadplay.player.ui.components.ImportFab
import com.roadplay.player.ui.components.TextInputDialog
import com.roadplay.player.ui.components.VideoRow
import com.roadplay.player.ui.components.rememberImportActions

@Composable
fun LibraryScreen(
    vm: LibraryViewModel,
    padding: PaddingValues,
    onPlay: (List<VideoEntity>, VideoEntity) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importActions = rememberImportActions(vm::importVideos, vm::importFolder)
    var tab by remember { mutableIntStateOf(0) }
    var addTo by remember { mutableStateOf<VideoEntity?>(null) }
    var removing by remember { mutableStateOf<VideoEntity?>(null) }
    var newPlaylist by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().padding(padding)) {
        Column(Modifier.fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Videos") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Playlists") })
            }
            if (tab == 0) {
                VideosTab(vm, state.sorted(), state.query, state.settings.sortField, state.settings.sortAscending,
                    state.videos.isEmpty() && state.loaded, onPlay,
                    onAddToPlaylist = { addTo = it }, onRemove = { removing = it })
            } else {
                Column(Modifier.fillMaxSize()) {
                    Button(onClick = { newPlaylist = true }, modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Rounded.Add, null); Text("  New playlist")
                    }
                    if (state.playlists.isEmpty()) {
                        EmptyState(Icons.Rounded.VideoLibrary, "No playlists", "Create a playlist, then add videos from the Videos tab.")
                    }
                    LazyColumn {
                        items(state.playlists, key = { it.id }) { p ->
                            ListItem(
                                headlineContent = { Text(p.name) },
                                supportingContent = { Text(if (p.count == 1) "1 video" else "${p.count} videos") },
                                modifier = Modifier.clickable { onOpenPlaylist(p.id) },
                            )
                        }
                    }
                }
            }
        }
        if (tab == 0) ImportFab(importActions, state.importing, Modifier.align(Alignment.BottomEnd).padding(16.dp))
    }

    addTo?.let { video ->
        AddToPlaylistDialog(
            playlists = state.playlists,
            onPick = { vm.addToPlaylist(it.id, video.id) },
            onCreate = { name -> vm.createPlaylist(name, listOf(video.id)) },
            onDismiss = { addTo = null },
        )
    }
    removing?.let { video ->
        ConfirmDialog(
            title = "Remove from library?",
            text = "“${video.label()}” will be removed from RoadPlay. The original file on your phone is not deleted.",
            confirmLabel = "Remove",
            onConfirm = { vm.remove(video) },
            onDismiss = { removing = null },
        )
    }
    if (newPlaylist) {
        TextInputDialog("New playlist", "Playlist name", "Create", onConfirm = { vm.createPlaylist(it) }, onDismiss = { newPlaylist = false })
    }
}

@Composable
private fun VideosTab(
    vm: LibraryViewModel,
    videos: List<VideoEntity>,
    query: String,
    sortField: SortField,
    ascending: Boolean,
    libraryEmpty: Boolean,
    onPlay: (List<VideoEntity>, VideoEntity) -> Unit,
    onAddToPlaylist: (VideoEntity) -> Unit,
    onRemove: (VideoEntity) -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Column {
        OutlinedTextField(
            value = query, onValueChange = vm::setQuery, singleLine = true,
            placeholder = { Text("Search by name or title") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                AssistChip(
                    onClick = { sortMenu = true },
                    label = { Text("Sort: ${sortField.label} ${if (ascending) "↑" else "↓"}") },
                )
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortField.values().forEach { f ->
                        DropdownMenuItem(
                            text = { Text(f.label + if (f == sortField) (if (ascending) "  ↑" else "  ↓") else "") },
                            onClick = {
                                // Tapping the active field flips direction; a new field keeps the old direction.
                                vm.setSort(f, if (f == sortField) !ascending else ascending)
                                sortMenu = false
                            },
                        )
                    }
                }
            }
        }
        if (libraryEmpty) {
            EmptyState(Icons.Rounded.VideoLibrary, "Your library is empty", "Tap “Import videos” to add files or a whole folder.")
        } else if (videos.isEmpty()) {
            EmptyState(Icons.Rounded.Search, "No matches", "Try a different search.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(videos, key = { it.id }) { v ->
                VideoRow(
                    video = v,
                    onClick = { if (v.isAvailable) onPlay(videos.filter { it.isAvailable }, v) else onRemove(v) },
                    onToggleFavourite = { vm.toggleFavourite(v) },
                    extraMenu = { close ->
                        DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { close(); onAddToPlaylist(v) })
                        DropdownMenuItem(
                            text = { Text("Remove from library") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                            onClick = { close(); onRemove(v) },
                        )
                    },
                )
            }
        }
    }
}
