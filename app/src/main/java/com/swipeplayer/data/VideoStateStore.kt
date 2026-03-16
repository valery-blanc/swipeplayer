package com.swipeplayer.data

import android.content.Context
import android.content.SharedPreferences
import com.swipeplayer.ui.DisplayMode
import com.swipeplayer.ui.PlaybackOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists per-video playback state (position, zoom, display mode) using
 * SharedPreferences. The key is the filename alone (not the full URI path)
 * so that state survives the file being moved between directories.
 *
 * Position is not saved if the video was played to near the end (last 5s)
 * or watched less than 5s — in both cases the video restarts from the beginning.
 */
@Singleton
class VideoStateStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("swipeplayer_video_prefs", Context.MODE_PRIVATE)
    }

    data class VideoState(
        val positionMs: Long,
        val zoom: Float,
        val displayMode: DisplayMode,
    )

    fun save(filename: String, positionMs: Long, durationMs: Long, zoom: Float, displayMode: DisplayMode) {
        val safePosition = when {
            positionMs < 5_000L                        -> 0L  // watched < 5s: restart
            durationMs > 0 && positionMs >= durationMs - 5_000L -> 0L  // near end: restart
            else                                        -> positionMs
        }
        // CRO-031: use "::" separator to avoid key collisions for filenames containing "_pos" etc.
        prefs.edit()
            .putLong("${filename}::pos", safePosition)
            .putFloat("${filename}::zoom", zoom)
            .putString("${filename}::fmt", displayMode.name)
            .apply()
    }

    fun savePlaybackOrder(order: PlaybackOrder) {
        prefs.edit().putString("global::playback_order", order.name).apply()
    }

    fun loadPlaybackOrder(): PlaybackOrder =
        prefs.getString("global::playback_order", PlaybackOrder.RANDOM.name)
            ?.let { runCatching { PlaybackOrder.valueOf(it) }.getOrNull() }
            ?: PlaybackOrder.RANDOM

    fun load(filename: String): VideoState? {
        if (!prefs.contains("${filename}::pos")) return null
        val pos  = prefs.getLong("${filename}::pos", 0L)
        val zoom = prefs.getFloat("${filename}::zoom", 1f)
        val fmt  = prefs.getString("${filename}::fmt", DisplayMode.ADAPT.name)
            ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
            ?: DisplayMode.ADAPT
        return VideoState(positionMs = pos, zoom = zoom, displayMode = fmt)
    }
}
