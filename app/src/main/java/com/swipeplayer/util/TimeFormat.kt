package com.swipeplayer.util

/**
 * Formats a duration in milliseconds as a human-readable timecode string.
 *
 * Format:
 *   < 1 hour  -> "MM:SS"       e.g. 183_000 ms -> "03:03"
 *   >= 1 hour -> "HH:MM:SS"    e.g. 3_723_000 ms -> "01:02:03"
 *
 * Negative values are clamped to 0. No upper-bound limit (handles > 4 h).
 */
fun Long.toTimecode(): String {
    val totalSeconds = (coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
