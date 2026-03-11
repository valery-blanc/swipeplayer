package com.swipeplayer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.swipeplayer.util.sortedNaturally
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SwipePlayer"

/**
 * Lists video files from the directory that contains the given URI.
 *
 * Dispatch strategy (tried in order until one returns a non-empty list):
 *   file://           - File.parentFile.listFiles()
 *   content://media   - MediaStore query by BUCKET_ID (all volumes)
 *   content://(other) - 1) SAF DocumentFile parent listing
 *                       2) MediaStore query by RELATIVE_PATH / DATA (all volumes)
 *                       3) direct File listing via extracted URI path (best-effort)
 *   other             - single-file fallback (list of 1 element)
 *
 * All I/O runs on Dispatchers.IO. Never throws; always returns at
 * least a single-element list containing the original URI.
 */
@Singleton
class VideoRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contentResolver: ContentResolver,
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    suspend fun listVideosInDirectory(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            Log.d(TAG, "listVideosInDirectory: scheme=$scheme authority=${uri.authority} path=${uri.path}")

            val list: List<VideoFile> = when {
                scheme == "file" -> listViaFile(uri)
                scheme == "content" && isMediaStoreUri(uri) -> queryMediaStoreByBucketId(uri)
                scheme == "content" -> listViaContentFallbackChain(uri)
                else -> emptyList()
            }
            Log.d(TAG, "listVideosInDirectory: found ${list.size} video(s)")
            list.ifEmpty { listOf(buildFallbackVideoFile(uri)) }
        }

    suspend fun resolveVideoFile(uri: Uri): VideoFile? =
        withContext(Dispatchers.IO) {
            runCatching { buildFallbackVideoFile(uri) }.getOrNull()
        }

    // -------------------------------------------------------------------------
    // URI classification
    // -------------------------------------------------------------------------

    fun isMediaStoreUri(uri: Uri): Boolean =
        uri.authority?.equals("media", ignoreCase = true) == true

    fun isSafUri(uri: Uri): Boolean =
        uri.authority?.contains("documents", ignoreCase = true) == true ||
            uri.authority?.contains("com.android.externalstorage", ignoreCase = true) == true

    // -------------------------------------------------------------------------
    // Listing strategies
    // -------------------------------------------------------------------------

    private suspend fun listViaFile(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val file = File(uri.path ?: return@withContext emptyList())
            val parent = file.parentFile ?: return@withContext emptyList()
            parent.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
                ?.map { f ->
                    VideoFile(
                        uri = Uri.fromFile(f),
                        name = f.name,
                        path = f.absolutePath,
                        duration = -1L,
                        size = f.length(),
                    )
                }
                ?.sortedNaturally()
                ?: emptyList()
        }

    /**
     * For content:// non-MediaStore URIs (e.g. FileProvider from a file manager):
     *   1. SAF DocumentFile — works when a tree URI permission was granted
     *   2. MediaStore by directory path — works for visible, indexed files;
     *      uses RELATIVE_PATH (API 29+) across all storage volumes to correctly
     *      cover SD cards, which are separate volumes from the primary storage
     *   3. Direct File listing via URI path — works on older Android or when
     *      MANAGE_EXTERNAL_STORAGE is granted
     */
    private suspend fun listViaContentFallbackChain(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val safList = resolveViaDocumentFile(uri)
            if (safList.isNotEmpty()) {
                Log.d(TAG, "listViaContentFallbackChain: SAF found ${safList.size} files")
                return@withContext safList
            }

            val mediaList = queryMediaStoreByPath(uri)
            if (mediaList.isNotEmpty()) {
                Log.d(TAG, "listViaContentFallbackChain: MediaStore-path found ${mediaList.size} files")
                return@withContext mediaList
            }

            val fileList = resolveViaUriPath(uri)
            if (fileList.isNotEmpty()) {
                Log.d(TAG, "listViaContentFallbackChain: file-path found ${fileList.size} files")
                return@withContext fileList
            }

            Log.d(TAG, "listViaContentFallbackChain: all strategies failed, single-file mode")
            emptyList()
        }

    // -------------------------------------------------------------------------
    // MediaStore helpers
    // -------------------------------------------------------------------------

    /** Returns all external volume content URIs for video (covers primary + SD cards). */
    private fun allVideoVolumeUris(): List<Uri> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getExternalVolumeNames(context).map { volumeName ->
                MediaStore.Video.Media.getContentUri(volumeName)
            }
        } else {
            listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        }

    /**
     * Returns the volume root path for a MediaStore volume name.
     * "external_primary" -> /storage/emulated/0
     * "3035-6338"        -> /storage/3035-6338
     */
    private fun volumeRootPath(volumeName: String): String =
        if (volumeName == "external_primary" || volumeName == "external") {
            "/storage/emulated/0"
        } else {
            "/storage/$volumeName"
        }

    private val videoProjection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DATA,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
    )

    private fun Cursor.toVideoFile(volumeUri: Uri): VideoFile? {
        val nameCol = getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val idCol   = getColumnIndex(MediaStore.Video.Media._ID)
        val dataCol = getColumnIndex(MediaStore.Video.Media.DATA)
        val durCol  = getColumnIndex(MediaStore.Video.Media.DURATION)
        val sizeCol = getColumnIndex(MediaStore.Video.Media.SIZE)
        if (nameCol < 0 || idCol < 0) return null
        val name = getString(nameCol) ?: return null
        if (name.substringAfterLast('.', "").lowercase() !in VIDEO_EXTENSIONS) return null
        val id = getLong(idCol)
        return VideoFile(
            uri      = ContentUris.withAppendedId(volumeUri, id),
            name     = name,
            path     = if (dataCol >= 0) getString(dataCol) ?: "" else "",
            duration = if (durCol >= 0) getLong(durCol) else -1L,
            size     = if (sizeCol >= 0) getLong(sizeCol) else 0L,
        )
    }

    /**
     * Queries MediaStore across all external volumes by BUCKET_ID.
     * The bucket ID is obtained from the original URI (which was passed via Intent,
     * so we have permission to read it even without READ_MEDIA_VIDEO).
     */
    private suspend fun queryMediaStoreByBucketId(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val bucketId = getBucketId(uri) ?: return@withContext emptyList()
            val result = mutableListOf<VideoFile>()
            for (volumeUri in allVideoVolumeUris()) {
                runCatching {
                    contentResolver.query(
                        volumeUri,
                        videoProjection,
                        "${MediaStore.Video.Media.BUCKET_ID} = ?",
                        arrayOf(bucketId.toString()),
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) cursor.toVideoFile(volumeUri)?.let { result += it }
                    }
                }
            }
            result.sortedNaturally()
        }

    /**
     * Queries MediaStore across all external volumes by the parent directory of the given URI.
     *
     * On Android 10+ (API 29): uses RELATIVE_PATH per volume — more reliable than
     * DATA LIKE since the DATA column is deprecated and not always populated.
     * On older Android: falls back to DATA LIKE query on EXTERNAL_CONTENT_URI.
     *
     * For SD cards: RELATIVE_PATH is relative to the SD card root, e.g.
     *   /storage/3035-6338/Movies/hh/  ->  RELATIVE_PATH = "Movies/hh/"
     */
    private suspend fun queryMediaStoreByPath(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val path = uri.path ?: return@withContext emptyList()
            val parentPath = File(path).parent ?: return@withContext emptyList()
            Log.d(TAG, "queryMediaStoreByPath: parentPath=$parentPath")

            val result = mutableListOf<VideoFile>()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For each volume, check if the parent path belongs to it and query
                // using RELATIVE_PATH (more reliable than DATA on API 29+).
                for (volumeName in MediaStore.getExternalVolumeNames(context)) {
                    val volRoot = volumeRootPath(volumeName)
                    if (!parentPath.startsWith(volRoot)) continue

                    // e.g. /storage/3035-6338/Movies/hh  ->  Movies/hh/
                    val relPath = parentPath.removePrefix(volRoot).trimStart('/').let {
                        if (it.isEmpty()) "" else "$it/"
                    }
                    Log.d(TAG, "queryMediaStoreByPath: volume=$volumeName relPath=$relPath")

                    val volumeUri = MediaStore.Video.Media.getContentUri(volumeName)
                    runCatching {
                        contentResolver.query(
                            volumeUri,
                            videoProjection,
                            "${MediaStore.Video.Media.RELATIVE_PATH} = ?",
                            arrayOf(relPath),
                            null,
                        )?.use { cursor ->
                            while (cursor.moveToNext()) cursor.toVideoFile(volumeUri)?.let { result += it }
                        }
                    }.onFailure { Log.w(TAG, "queryMediaStoreByPath volume=$volumeName failed: $it") }
                }
            } else {
                runCatching {
                    contentResolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        videoProjection,
                        "${MediaStore.Video.Media.DATA} LIKE ?",
                        arrayOf("$parentPath/%"),
                        null,
                    )?.use { cursor ->
                        while (cursor.moveToNext()) {
                            cursor.toVideoFile(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                                ?.let { result += it }
                        }
                    }
                }
            }

            result.sortedNaturally()
        }

    private suspend fun resolveViaDocumentFile(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            try {
                val singleDoc = DocumentFile.fromSingleUri(context, uri)
                    ?: return@withContext emptyList()
                val parentDoc = singleDoc.parentFile
                    ?: return@withContext emptyList()
                parentDoc.listFiles()
                    .filter { doc ->
                        doc.isFile &&
                            doc.name?.substringAfterLast('.', "")?.lowercase() in VIDEO_EXTENSIONS
                    }
                    .map { doc ->
                        VideoFile(
                            uri      = doc.uri,
                            name     = doc.name ?: doc.uri.lastPathSegment ?: "",
                            path     = "",
                            duration = -1L,
                            size     = doc.length(),
                        )
                    }
                    .sortedNaturally()
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** Best-effort: reads the file system path embedded in FileProvider-style URIs. */
    private suspend fun resolveViaUriPath(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            try {
                val path = uri.path ?: return@withContext emptyList()
                val parent = File(path).parentFile ?: return@withContext emptyList()
                Log.d(TAG, "resolveViaUriPath: parent=${parent.path} exists=${parent.exists()}")
                parent.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }
                    ?.map { f ->
                        VideoFile(
                            uri      = Uri.fromFile(f),
                            name     = f.name,
                            path     = f.absolutePath,
                            duration = -1L,
                            size     = f.length(),
                        )
                    }
                    ?.sortedNaturally()
                    ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun getBucketId(uri: Uri): Long? =
        withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media.BUCKET_ID),
                    null, null, null,
                )?.use { cursor ->
                    if (cursor.moveToFirst())
                        cursor.getLong(
                            cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_ID)
                        )
                    else null
                }
            }.getOrNull()
        }

    private suspend fun buildFallbackVideoFile(uri: Uri): VideoFile =
        withContext(Dispatchers.IO) {
            val name = getDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
            VideoFile(uri = uri, name = name, path = uri.path ?: "", duration = -1L)
        }

    private fun getDisplayName(uri: Uri): String? =
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
}
