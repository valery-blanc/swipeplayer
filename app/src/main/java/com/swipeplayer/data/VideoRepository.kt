package com.swipeplayer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.swipeplayer.util.sortedNaturally
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lists video files from the directory that contains the given URI.
 *
 * Dispatch strategy:
 *   file://         - File.parentFile.listFiles()
 *   content://media - MediaStore query by BUCKET_ID
 *   content://(any) - DocumentFile SAF listing
 *   other           - single-file fallback (list of 1 element)
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

    /**
     * Returns a naturally-sorted list of all video files in the same directory
     * as [uri]. Falls back to a single-element list when the directory is
     * inaccessible (e.g. a raw content URI without SAF permission).
     */
    suspend fun listVideosInDirectory(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            val list: List<VideoFile> = when {
                scheme == "file" -> listViaFile(uri)
                scheme == "content" && isMediaStoreUri(uri) -> queryMediaStore(uri)
                scheme == "content" -> resolveViaDocumentFile(uri)
                else -> emptyList()
            }
            list.ifEmpty { listOf(buildFallbackVideoFile(uri)) }
        }

    /**
     * Resolves [uri] to a [VideoFile] using the display name from
     * OpenableColumns. Returns null only if the URI is completely unresolvable.
     */
    suspend fun resolveVideoFile(uri: Uri): VideoFile? =
        withContext(Dispatchers.IO) {
            runCatching { buildFallbackVideoFile(uri) }.getOrNull()
        }

    // -------------------------------------------------------------------------
    // URI classification
    // -------------------------------------------------------------------------

    /** True for URIs served by the MediaStore provider (authority == "media"). */
    fun isMediaStoreUri(uri: Uri): Boolean =
        uri.authority?.equals("media", ignoreCase = true) == true

    /**
     * True for Storage Access Framework (SAF) document URIs.
     * These have authorities containing "documents" or "externalstorage".
     */
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

    private suspend fun queryMediaStore(uri: Uri): List<VideoFile> =
        withContext(Dispatchers.IO) {
            val bucketId = getBucketId(uri) ?: return@withContext emptyList()

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
            )
            val selection = "${MediaStore.Video.Media.BUCKET_ID} = ?"
            val selectionArgs = arrayOf(bucketId.toString())

            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val result = mutableListOf<VideoFile>()
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    if (name.substringAfterLast('.', "").lowercase() !in VIDEO_EXTENSIONS) continue
                    val id = cursor.getLong(idCol)
                    result += VideoFile(
                        uri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        name = name,
                        path = cursor.getString(dataCol) ?: "",
                        duration = cursor.getLong(durCol),
                        size = cursor.getLong(sizeCol),
                    )
                }
                result.sortedNaturally()
            } ?: emptyList()
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
                            uri = doc.uri,
                            name = doc.name ?: doc.uri.lastPathSegment ?: "",
                            path = "",
                            duration = -1L,
                            size = doc.length(),
                        )
                    }
                    .sortedNaturally()
            } catch (e: SecurityException) {
                emptyList()
            } catch (e: Exception) {
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

    /** Builds a single [VideoFile] from [uri] alone, without directory listing. */
    private suspend fun buildFallbackVideoFile(uri: Uri): VideoFile =
        withContext(Dispatchers.IO) {
            val name = getDisplayName(uri) ?: uri.lastPathSegment ?: uri.toString()
            VideoFile(
                uri = uri,
                name = name,
                path = uri.path ?: "",
                duration = -1L,
            )
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
