package com.roadplay.player.data.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.roadplay.player.data.db.PlaylistDao
import com.roadplay.player.data.db.PlaylistEntity
import com.roadplay.player.data.db.VideoDao
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

data class PlaylistSummary(val id: Long, val name: String, val count: Int)

data class ImportResult(val added: Int, val skipped: Int, val failed: Int) {
    fun summary(): String = buildString {
        append(if (added == 1) "Added 1 video" else "Added $added videos")
        if (skipped > 0) append(", $skipped already in library")
        if (failed > 0) append(", $failed could not be read")
    }
}

class LibraryRepository(
    private val context: Context,
    private val videoDao: VideoDao,
    private val playlistDao: PlaylistDao,
) {
    private enum class Outcome { ADDED, SKIPPED, FAILED }
    private data class FileInfo(val name: String?, val size: Long, val modified: Long)
    private data class VideoMeta(val durationMs: Long, val width: Int, val height: Int, val hasAudio: Boolean, val title: String?)

    val videos: Flow<List<VideoEntity>> = videoDao.observeAll()

    val playlists: Flow<List<PlaylistSummary>> =
        combine(playlistDao.observePlaylists(), playlistDao.observeAllItems()) { lists, items ->
            val counts = items.groupingBy { it.playlistId }.eachCount()
            lists.map { PlaylistSummary(it.id, it.name, counts[it.id] ?: 0) }
        }

    fun playlistVideos(playlistId: Long): Flow<List<VideoEntity>> = playlistDao.observeVideos(playlistId)

    // ---- Import ---------------------------------------------------------------------------

    /** Import individual documents picked with ACTION_OPEN_DOCUMENT. */
    suspend fun importVideos(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        var added = 0; var skipped = 0; var failed = 0
        for (uri in uris) {
            persistReadPermission(uri)
            when (addOne(uri, null)) {
                Outcome.ADDED -> added++
                Outcome.SKIPPED -> skipped++
                Outcome.FAILED -> failed++
            }
        }
        ImportResult(added, skipped, failed)
    }

    /** Import every video below a folder picked with ACTION_OPEN_DOCUMENT_TREE. */
    suspend fun importFolder(tree: Uri): ImportResult = withContext(Dispatchers.IO) {
        persistReadPermission(tree)
        val root = DocumentFile.fromTreeUri(context, tree)
        if (root == null || !root.isDirectory) return@withContext ImportResult(0, 0, 1)
        val found = mutableListOf<DocumentFile>()
        collect(root, 0, found)
        var added = 0; var skipped = 0; var failed = 0
        for (doc in found) {
            when (addOne(doc.uri, doc)) {
                Outcome.ADDED -> added++
                Outcome.SKIPPED -> skipped++
                Outcome.FAILED -> failed++
            }
        }
        ImportResult(added, skipped, failed)
    }

    private fun collect(dir: DocumentFile, depth: Int, out: MutableList<DocumentFile>) {
        if (depth > MAX_FOLDER_DEPTH) return
        val children = try { dir.listFiles() } catch (e: Exception) { AppLog.w("listFiles failed", e); return }
        for (f in children) {
            if (f.isDirectory) collect(f, depth + 1, out)
            else if (f.type?.startsWith("video/") == true) out += f
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Some providers do not offer persistable grants; the video still plays this session.
            AppLog.w("Could not persist permission for $uri", e)
        }
    }

    private suspend fun addOne(uri: Uri, doc: DocumentFile?): Outcome {
        val key = uri.toString()
        if (videoDao.getByUri(key) != null) return Outcome.SKIPPED
        val type = try { context.contentResolver.getType(uri) } catch (e: Exception) { null }
        if (type != null && !type.startsWith("video/")) return Outcome.FAILED
        if (!isReadable(uri)) return Outcome.FAILED
        val info = readFileInfo(uri)
        val meta = readMetadata(uri)
        val entity = VideoEntity(
            uri = key,
            displayName = doc?.name ?: info.name ?: uri.lastPathSegment ?: "Video",
            title = meta.title,
            durationMs = meta.durationMs,
            sizeBytes = doc?.length()?.takeIf { it > 0 } ?: info.size,
            width = meta.width,
            height = meta.height,
            hasAudio = meta.hasAudio,
            addedAt = System.currentTimeMillis(),
            fileModifiedAt = doc?.lastModified()?.takeIf { it > 0 } ?: info.modified,
        )
        return if (videoDao.insert(entity) > 0) Outcome.ADDED else Outcome.SKIPPED
    }

    private fun readFileInfo(uri: Uri): FileInfo {
        var name: String? = null; var size = 0L; var modified = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (n >= 0 && !c.isNull(n)) name = c.getString(n)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                    val m = c.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                    if (m >= 0 && !c.isNull(m)) modified = c.getLong(m)
                }
            }
        } catch (e: Exception) {
            AppLog.w("Could not query file info for $uri", e)
        }
        return FileInfo(name, size, modified)
    }

    private fun readMetadata(uri: Uri): VideoMeta {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            VideoMeta(
                durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                width = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                height = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) != null,
                title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
            )
        } catch (e: Exception) {
            AppLog.w("Metadata unreadable for $uri (it may still play)", e)
            VideoMeta(0, 0, 0, true, null)
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    // ---- Availability -----------------------------------------------------------------------

    fun isReadable(uri: Uri): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        false // moved, deleted, or permission revoked
    }

    /** Re-checks every video and flags the ones that can no longer be opened. */
    suspend fun refreshAvailability() = withContext(Dispatchers.IO) {
        for (v in videoDao.getAll()) {
            val ok = isReadable(Uri.parse(v.uri))
            if (ok != v.isAvailable) videoDao.setAvailable(v.id, ok)
        }
    }

    suspend fun markUnavailable(id: Long) = videoDao.setAvailable(id, false)

    // ---- Library edits --------------------------------------------------------------------

    suspend fun setFavourite(id: Long, favourite: Boolean) = videoDao.setFavourite(id, favourite)

    /** Removes the entry from RoadPlay only. The original file is never touched. */
    suspend fun removeFromLibrary(video: VideoEntity) = withContext(Dispatchers.IO) {
        videoDao.delete(video.id)
        if (!video.uri.contains("/tree/")) {
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(video.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun clearHistory() = videoDao.clearHistory()

    // ---- Playlists --------------------------------------------------------------------------

    suspend fun createPlaylist(name: String, initialVideoIds: List<Long> = emptyList()): Long {
        val id = playlistDao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis()))
        if (initialVideoIds.isNotEmpty()) playlistDao.addVideos(id, initialVideoIds)
        return id
    }

    suspend fun deletePlaylist(id: Long) = playlistDao.deletePlaylist(id)
    suspend fun addToPlaylist(playlistId: Long, videoIds: List<Long>) = playlistDao.addVideos(playlistId, videoIds)
    suspend fun removeFromPlaylist(playlistId: Long, videoId: Long) = playlistDao.removeItem(playlistId, videoId)
    suspend fun playlistName(id: Long): String? = playlistDao.getPlaylist(id)?.name

    private companion object {
        const val MAX_FOLDER_DEPTH = 6
    }
}
