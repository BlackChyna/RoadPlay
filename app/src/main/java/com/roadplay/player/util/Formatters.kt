package com.roadplay.player.util

import java.util.Locale

/** 3_725_000 ms -> "1:02:05", 65_000 ms -> "1:05". Negative/unknown -> "0:00". */
fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** Human readable file size using 1024 steps. */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var i = 0
    while (value >= 1024 && i < units.lastIndex) { value /= 1024; i++ }
    return if (i == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[i])
}
