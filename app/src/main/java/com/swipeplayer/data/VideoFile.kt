package com.swipeplayer.data

import android.net.Uri

/**
 * Represents a single video file in the playlist.
 * [uri]      Canonical URI used by ExoPlayer (content:// or file://).
 * [name]     Filename with extension, used for display and natural sort.
 * [path]     Absolute filesystem path; empty string for pure content:// URIs.
 * [duration] Duration in milliseconds; -1 if unknown at listing time.
 * [size]     File size in bytes; 0 if unavailable.
 */
data class VideoFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val duration: Long,
    val size: Long = 0L,
)

/** Supported video file extensions (lowercase). */
val VIDEO_EXTENSIONS: Set<String> = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv",
    "webm", "m4v", "3gp", "ts", "mpg", "mpeg",
)
