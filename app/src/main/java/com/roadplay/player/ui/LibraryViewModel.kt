package com.roadplay.player.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadplay.player.data.db.VideoEntity
import com.roadplay.player.data.library.ImportResult
import com.roadplay.player.data.library.LibraryRepository
import com.roadplay.player.data.library.PlaylistSummary
import com.roadplay.player.data.library.VideoQueries
import com.roadplay.player.data.settings.AppSettings
import com.roadplay.player.data.settings.SettingsRepository
import com.roadplay.player.data.settings.SortField
import com.roadplay.player.util.AppLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val videos: List<VideoEntity> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val query: String = "",
    val message: String? = null,
    val importing: Boolean = false,
    val loaded: Boolean = false,
) {
    fun sorted(): List<VideoEntity> =
        VideoQueries.sort(VideoQueries.search(videos, query), settings.sortField, settings.sortAscending)
    fun continueWatching() = VideoQueries.continueWatching(videos)
    fun recentlyPlayed() = VideoQueries.recentlyPlayed(videos)
    fun favourites() = VideoQueries.favourites(videos)
    fun recentlyAdded() = VideoQueries.recentlyAdded(videos)
}

class LibraryViewModel(
    private val repo: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<String?>(null)
    private val importing = MutableStateFlow(false)

    val settings: StateFlow<AppSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val data = combine(repo.videos, repo.playlists, settings) { v, p, s -> Triple(v, p, s) }
    private val transient = combine(query, message, importing) { q, m, i -> Triple(q, m, i) }

    val state: StateFlow<LibraryUiState> = combine(data, transient) { (v, p, s), (q, m, i) ->
        LibraryUiState(v, p, s, q, m, i, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setQuery(q: String) { query.value = q }
    fun dismissMessage() { message.value = null }

    fun importVideos(uris: List<Uri>) = runImport { repo.importVideos(uris) }
    fun importFolder(tree: Uri) = runImport { repo.importFolder(tree) }

    private fun runImport(block: suspend () -> ImportResult) {
        viewModelScope.launch {
            importing.value = true
            try {
                message.value = block().summary()
            } catch (e: Exception) {
                AppLog.e("Import failed", e)
                message.value = "Import failed: ${e.message ?: "unknown error"}"
            } finally {
                importing.value = false
            }
        }
    }

    fun refreshAvailability() { viewModelScope.launch { repo.refreshAvailability() } }
    fun toggleFavourite(v: VideoEntity) { viewModelScope.launch { repo.setFavourite(v.id, !v.isFavourite) } }
    fun remove(v: VideoEntity) {
        viewModelScope.launch {
            repo.removeFromLibrary(v)
            message.value = "Removed from library. The original file was not deleted."
        }
    }

    fun setSort(field: SortField, ascending: Boolean) { viewModelScope.launch { settingsRepo.setSort(field, ascending) } }

    fun createPlaylist(name: String, videoIds: List<Long> = emptyList()) {
        if (name.isBlank()) return
        viewModelScope.launch { repo.createPlaylist(name, videoIds); message.value = "Playlist “${name.trim()}” created" }
    }
    fun addToPlaylist(playlistId: Long, videoId: Long) {
        viewModelScope.launch { repo.addToPlaylist(playlistId, listOf(videoId)); message.value = "Added to playlist" }
    }
    fun deletePlaylist(id: Long) { viewModelScope.launch { repo.deletePlaylist(id) } }
    fun removeFromPlaylist(playlistId: Long, videoId: Long) {
        viewModelScope.launch { repo.removeFromPlaylist(playlistId, videoId) }
    }

    fun playlistVideos(id: Long): Flow<List<VideoEntity>> = repo.playlistVideos(id)
    suspend fun playlistName(id: Long): String? = repo.playlistName(id)
}
