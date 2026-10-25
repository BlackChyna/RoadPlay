package com.roadplay.player.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import com.roadplay.player.data.library.LibraryRepository
import com.roadplay.player.data.settings.AppSettings
import com.roadplay.player.data.settings.SettingsRepository
import com.roadplay.player.data.settings.SortField
import com.roadplay.player.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val context: Context,
    private val repo: SettingsRepository,
    private val library: LibraryRepository,
) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val versionName: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) { "?" }

    fun setTheme(m: ThemeMode) { viewModelScope.launch { repo.setTheme(m) } }
    fun setSpeed(s: Float) { viewModelScope.launch { repo.setDefaultSpeed(s) } }
    fun setResume(b: Boolean) { viewModelScope.launch { repo.setResume(b) } }
    fun setAutoSubtitles(b: Boolean) { viewModelScope.launch { repo.setAutoSubtitles(b) } }
    fun setSubtitleScale(s: Float) { viewModelScope.launch { repo.setSubtitleScale(s) } }
    fun setSort(f: SortField, asc: Boolean) { viewModelScope.launch { repo.setSort(f, asc) } }

    fun clearHistory(onDone: () -> Unit) { viewModelScope.launch { library.clearHistory(); onDone() } }

    /** Clears Coil's thumbnail caches. Imported videos and settings are untouched. */
    fun clearCache(onDone: () -> Unit) {
        viewModelScope.launch {
            val loader = context.imageLoader
            loader.memoryCache?.clear()
            loader.diskCache?.clear()
            onDone()
        }
    }
}
