package com.swipeplayer.ui

import com.swipeplayer.data.VideoFile

/**
 * Immutable snapshot of the entire player UI state, emitted via StateFlow.
 *
 * Default values reflect the initial state when the app launches:
 *   - No video loaded yet (currentVideo = null)
 *   - Controls visible (user can see the UI on first open)
 *   - Display mode: ADAPT (fit with letterboxing)
 *   - Orientation: AUTO (follows sensor)
 *   - Brightness: -1f (use system brightness)
 *   - Volume: 1f (full volume)
 */
data class PlayerUiState(

    // --- Playlist ------------------------------------------------------------

    /** All video files in the current directory, sorted naturally. */
    val playlist: List<VideoFile> = emptyList(),

    /** The video currently on screen. */
    val currentVideo: VideoFile? = null,

    /**
     * The video shown on page 0 of the VerticalPager (swipe-down target).
     * Null when currentIndex == 0 (no history to go back to).
     */
    val previousVideo: VideoFile? = null,

    /**
     * Whether swipe-to-navigate is enabled.
     * False when the directory contains only one video, or when a content://
     * URI with no directory access was provided.
     */
    val isSwipeEnabled: Boolean = true,

    // --- Playback ------------------------------------------------------------

    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,

    // --- Loading / error -----------------------------------------------------

    val isLoading: Boolean = false,
    val error: PlayerError? = null,

    // --- Controls UI ---------------------------------------------------------

    /** Whether the controls overlay (top bar, center controls, progress) is visible. */
    val controlsVisible: Boolean = true,

    // --- Zoom ----------------------------------------------------------------

    /** Current zoom scale applied via graphicsLayer on the video surface. */
    val zoomScale: Float = 1f,

    // --- Display & orientation -----------------------------------------------

    val displayMode: DisplayMode = DisplayMode.ADAPT,
    val orientationMode: OrientationMode = OrientationMode.AUTO,

    // --- Brightness & volume -------------------------------------------------

    /**
     * Current in-app brightness (0f..1f).
     * -1f means "use system brightness" (initial state, no override active).
     */
    val brightness: Float = -1f,

    /** Current media volume fraction (0f..1f). */
    val volume: Float = 1f,
)

// ---------------------------------------------------------------------------
// Supporting types
// ---------------------------------------------------------------------------

/** Video display / scaling mode. Cycles in this order when the user taps the format button. */
enum class DisplayMode {
    /** Fit entirely inside the screen, preserving aspect ratio (letterbox / pillarbox). */
    ADAPT,
    /** Fill the screen, cropping edges as needed (no black bars). */
    FILL,
    /** Stretch to fill the screen, ignoring aspect ratio. */
    STRETCH,
    /** Render at native pixel size (1:1). Content may be clipped on smaller screens. */
    NATIVE_100,
}

/** Screen orientation lock mode. Cycles when the user taps the orientation button. */
enum class OrientationMode {
    /** Follow the device sensor (default). */
    AUTO,
    /** Lock to landscape. */
    LANDSCAPE,
    /** Lock to portrait. */
    PORTRAIT,
}

/** Side of the screen where a double-tap occurred. */
enum class TapSide { LEFT, RIGHT }

/** Playback errors surfaced to the UI as toasts or overlays. */
sealed class PlayerError {
    /** Hardware decoder could not be initialised for this file's codec. */
    data object CodecNotSupported : PlayerError()

    /** The file was deleted or moved between listing and playback. */
    data object FileNotFound : PlayerError()

    /** A content:// URI was received but directory listing is not available. */
    data object ContentUriNoAccess : PlayerError()

    /** Catch-all for unexpected errors. */
    data class Generic(val message: String) : PlayerError()
}
