package com.roadplay.player.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.library.PlaylistSummary
import com.roadplay.player.data.library.PositionPolicy
import com.roadplay.player.util.formatDuration
import com.roadplay.player.util.formatSize

// ---- Import --------------------------------------------------------------------------------------

class ImportActions(val pickVideos: () -> Unit, val pickFolder: () -> Unit)

/** Storage Access Framework launchers: no storage permission required. */
@Composable
fun rememberImportActions(onVideos: (List<Uri>) -> Unit, onFolder: (Uri) -> Unit): ImportActions {
    val videos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) onVideos(uris)
    }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolder(uri)
    }
    return remember { ImportActions({ videos.launch(arrayOf("video/*")) }, { folder.launch(null) }) }
}

@Composable
fun ImportFab(actions: ImportActions, busy: Boolean, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        ExtendedFloatingActionButton(
            onClick = { open = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text(if (busy) "Importing…" else "Import videos") },
            modifier = Modifier.testTag("import_fab"),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Choose video files") },
                leadingIcon = { Icon(Icons.Rounded.VideoFile, null) },
                onClick = { open = false; actions.pickVideos() },
            )
            DropdownMenuItem(
                text = { Text("Choose a folder") },
                leadingIcon = { Icon(Icons.Rounded.FolderOpen, null) },
                onClick = { open = false; actions.pickFolder() },
            )
        }
    }
}

// ---- Thumbnails and cards ---------------------------------------------------------------------------

@Composable
fun VideoThumb(video: VideoEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Placeholder shows whenever the real frame is missing or still loading.
        Icon(
            Icons.Rounded.PlayCircle, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp),
        )
        if (video.isAvailable) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(Uri.parse(video.uri)).videoFrameMillis(1_000).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.LinkOff, contentDescription = "File unavailable", tint = Color.White)
            }
        }
        if (video.durationMs > 0) {
            Text(
                formatDuration(video.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        if (PositionPolicy.isInProgress(video) && video.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (video.positionMs.toFloat() / video.durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
    }
}

@Composable
fun VideoCard(video: VideoEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier
            .width(176.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${video.label()}" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        VideoThumb(video, Modifier.fillMaxWidth())
        Text(video.label(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PlaylistCard(playlist: PlaylistSummary, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(160.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(
                if (playlist.count == 1) "1 video" else "${playlist.count} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Library list row with favourite toggle and an overflow menu. */
@Composable
fun VideoRow(
    video: VideoEntity,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    extraMenu: @Composable (close: () -> Unit) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VideoThumb(video, Modifier.width(128.dp))
        Column(Modifier.weight(1f)) {
            Text(video.label(), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            val meta = buildString {
                if (!video.isAvailable) append("Unavailable · ")
                if (video.durationMs > 0) append(formatDuration(video.durationMs)).append(" · ")
                append(formatSize(video.sizeBytes))
            }
            Text(
                meta, style = MaterialTheme.typography.bodySmall,
                color = if (video.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onToggleFavourite) {
            Icon(
                if (video.isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (video.isFavourite) "Remove from favourites" else "Add to favourites",
                tint = if (video.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { extraMenu { menu = false } }
        }
    }
}

// ---- Generic building blocks -----------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title, style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp).testTag("empty_state"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, label: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value); onDismiss() }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<PlaylistSummary>,
    onPick: (PlaylistSummary) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog("New playlist", "Playlist name", "Create", onConfirm = onCreate, onDismiss = onDismiss)
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to playlist") },
        text = {
            if (playlists.isEmpty()) {
                Text("You have no playlists yet.")
            } else {
                LazyColumn {
                    items(playlists.size) { i ->
                        val p = playlists[i]
                        ListItem(
                            headlineContent = { Text(p.name) },
                            supportingContent = { Text(if (p.count == 1) "1 video" else "${p.count} videos") },
                            modifier = Modifier.clickable { onPick(p); onDismiss() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { creating = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
