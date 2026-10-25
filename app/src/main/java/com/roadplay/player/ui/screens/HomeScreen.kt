package com.roadplay.player.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadplay.player.R
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.ui.LibraryViewModel
import com.roadplay.player.ui.components.EmptyState
import com.roadplay.player.ui.components.ImportFab
import com.roadplay.player.ui.components.PlaylistCard
import com.roadplay.player.ui.components.SectionHeader
import com.roadplay.player.ui.components.VideoCard
import com.roadplay.player.ui.components.VideoRow
import com.roadplay.player.ui.components.rememberImportActions

@Composable
fun HomeScreen(
    vm: LibraryViewModel,
    padding: PaddingValues,
    onPlay: (List<VideoEntity>, VideoEntity) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val importActions = rememberImportActions(vm::importVideos, vm::importFolder)
    val searching = state.query.isNotBlank()

    Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                )
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    placeholder = { Text("Search your videos") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searching) IconButton(onClick = { vm.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("home_search"),
                )
            }

            if (state.loaded && state.videos.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.VideoLibrary,
                        title = "No videos yet",
                        body = "Import videos from your phone to start watching. RoadPlay never uploads or deletes your files.",
                    )
                }
            } else if (searching) {
                val results = state.sorted().filter { it.isAvailable }
                if (results.isEmpty()) item { Text("No matches", modifier = Modifier.padding(16.dp)) }
                items(results, key = { it.id }) { v ->
                    VideoRow(v, onClick = { onPlay(results, v) }, onToggleFavourite = { vm.toggleFavourite(v) }, extraMenu = {})
                }
            } else {
                carousel("Continue watching", state.continueWatching(), onPlay)
                carousel("Recently played", state.recentlyPlayed(), onPlay)
                carousel("Favourites", state.favourites(), onPlay)
                carousel("Recently added", state.recentlyAdded(), onPlay)
                item { SectionHeader("Playlists") }
                item {
                    if (state.playlists.isEmpty()) {
                        Text(
                            "Create playlists from the Library tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    } else {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.playlists, key = { it.id }) { p -> PlaylistCard(p) { onOpenPlaylist(p.id) } }
                        }
                    }
                }
            }
        }
        ImportFab(
            importActions, state.importing,
            Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.carousel(
    title: String,
    videos: List<VideoEntity>,
    onPlay: (List<VideoEntity>, VideoEntity) -> Unit,
) {
    val playable = videos.filter { it.isAvailable }
    if (playable.isEmpty()) return
    item { SectionHeader(title) }
    item {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(playable, key = { it.id }) { v -> VideoCard(v, onClick = { onPlay(playable, v) }) }
        }
    }
}
