package com.roadplay.player.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SortField(val label: String) {
    NAME("Name"), DATE_ADDED("Date added"), DURATION("Duration"), SIZE("File size")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultSpeed: Float = 1f,
    val resumePlayback: Boolean = true,
    val autoSubtitles: Boolean = true,
    val subtitleScale: Float = 1f,
    val sortField: SortField = SortField.DATE_ADDED,
    val sortAscending: Boolean = false,
)
