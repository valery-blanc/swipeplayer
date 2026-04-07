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
    val lastModified: Long = -1L,
)

/** Supported video file extensions (lowercase). */
val VIDEO_EXTENSIONS: Set<String> = setOf(
    "mp4", "mkv", "avi", "mov", "wmv", "flv",
    "webm", "m4v", "3gp", "ts", "mpg", "mpeg",
)

/** Supported external subtitle extensions (lowercase). */
val SUBTITLE_EXTENSIONS: Set<String> = setOf("srt", "ass", "ssa")

/**
 * Represents a folder (collection) that contains at least one video file.
 * [bucketId]      MediaStore BUCKET_ID.
 * [bucketName]    Display name of the folder.
 * [videoCount]    Number of videos in this folder.
 * [thumbnailUri]  URI of one video in the folder (used to load a thumbnail).
 * [folderPath]    Absolute filesystem path to the folder; empty for SD-card volumes.
 */
data class FolderInfo(
    val bucketId: Long,
    val bucketName: String,
    val videoCount: Int,
    val thumbnailUri: Uri,
    val folderPath: String,
)

/**
 * Represents a mounted storage volume (internal storage, SD card, USB drive, etc.)
 * used in the file browser to let the user switch between volumes.
 * [path]        Root directory of this volume.
 * [name]        Human-readable display name (e.g. "Stockage interne", "Carte SD").
 * [isRemovable] True for removable media (SD card, USB).
 */
data class StorageVolumeInfo(
    val path: java.io.File,
    val name: String,
    val isRemovable: Boolean,
)

/**
 * Represents an external subtitle file found alongside a video.
 * [mimeType] is a Media3 MIME type constant (e.g. MimeTypes.APPLICATION_SUBRIP).
 */
data class SubtitleFile(
    val uri: android.net.Uri,
    val mimeType: String,
    val name: String,
    val language: String? = null,
)
