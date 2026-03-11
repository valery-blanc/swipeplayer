package com.swipeplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory

/**
 * Central configuration for ExoPlayer instances and gesture thresholds.
 * All values are compile-time constants (or lazy singletons for Android objects).
 *
 * HW+ mode: hardware decoding only, no software fallback.
 * If the hardware decoder cannot handle a file, the app shows an error toast
 * and skips to the next video rather than falling back to software decoding.
 */
object PlayerConfig {

    // -------------------------------------------------------------------------
    // ExoPlayer buffer durations (milliseconds)
    // -------------------------------------------------------------------------

    const val MIN_BUFFER_MS: Int = 5_000
    const val MAX_BUFFER_MS: Int = 30_000
    const val BUFFER_FOR_PLAYBACK_MS: Int = 1_000
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS: Int = 2_000

    // -------------------------------------------------------------------------
    // Gesture zones (fraction of screen width)
    // -------------------------------------------------------------------------

    /** Left 15 % of the screen: vertical swipe controls brightness. */
    const val LEFT_ZONE_FRACTION: Float = 0.15f

    /** Right 15 % of the screen: vertical swipe controls volume. */
    const val RIGHT_ZONE_FRACTION: Float = 0.85f

    // -------------------------------------------------------------------------
    // Swipe-to-navigate thresholds
    // -------------------------------------------------------------------------

    /** Minimum vertical travel in dp required to trigger a video swipe. */
    const val SWIPE_MIN_DP: Float = 80f

    /** Minimum fling velocity in dp/s required to confirm a video swipe. */
    const val SWIPE_MIN_VELOCITY_DP: Float = 200f

    // -------------------------------------------------------------------------
    // Zoom
    // -------------------------------------------------------------------------

    const val MIN_ZOOM_SCALE: Float = 1f
    const val MAX_ZOOM_SCALE: Float = 50f

    // -------------------------------------------------------------------------
    // UI timings (milliseconds)
    // -------------------------------------------------------------------------

    /** Delay before controls auto-hide after last user interaction. */
    const val CONTROLS_HIDE_DELAY_MS: Long = 4_000L

    /** Controls fade-in / fade-out animation duration. */
    const val CONTROLS_FADE_DURATION_MS: Int = 200

    /** Swipe transition animation duration (VerticalPager). */
    const val SWIPE_TRANSITION_MS: Int = 300

    /** Double-tap seek feedback total duration (100 ms fade-in + 400 ms fade-out). */
    const val DOUBLE_TAP_FEEDBACK_MS: Int = 500

    /** Brightness / volume bar update animation duration. */
    const val SIDE_BAR_UPDATE_MS: Int = 100

    /** Delay before auto-skipping a video whose codec is unsupported. */
    const val CODEC_FAILURE_SKIP_DELAY_MS: Long = 2_000L

    /** Double-tap detection window: second tap must arrive within this time. */
    const val DOUBLE_TAP_WINDOW_MS: Long = 200L

    // -------------------------------------------------------------------------
    // ExoPlayer HW+ factory
    // -------------------------------------------------------------------------

    /**
     * Returns a [DefaultRenderersFactory] configured for HW+ mode:
     *   - setEnableDecoderFallback(false) — no software fallback
     *   - EXTENSION_RENDERER_MODE_OFF    — no extension renderers
     */
    @OptIn(UnstableApi::class)
    fun renderersFactory(context: Context): DefaultRenderersFactory =
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

    // -------------------------------------------------------------------------
    // ExoPlayer load control
    // -------------------------------------------------------------------------

    /**
     * Shared [DefaultLoadControl] instance.
     *
     * Note: DefaultLoadControl.Builder was removed in Media3 1.5.x.
     * The no-arg constructor uses library defaults (min=50s, max=50s).
     * The constants MIN_BUFFER_MS / MAX_BUFFER_MS above document the
     * intended target values; a custom LoadControl can be introduced
     * later if fine-grained tuning is required.
     */
    @OptIn(UnstableApi::class)
    val loadControl: DefaultLoadControl = DefaultLoadControl()
}
