package com.swipeplayer.ui.gesture

import com.swipeplayer.player.PlayerConfig

/**
 * Computes the new zoom scale from a two-finger pinch gesture.
 *
 * Scale = currentScale * (currentSpan / previousSpan), clamped to
 * [PlayerConfig.MIN_ZOOM_SCALE]..[PlayerConfig.MAX_ZOOM_SCALE] (1x..4x).
 */
class PinchZoomHandler {

    /**
     * @param previousSpan Distance between the two touch points in the previous frame.
     * @param currentSpan  Distance in the current frame.
     * @param currentScale The scale that was active before this update.
     * @return New scale, clamped to [1f, 4f].
     */
    fun calculateNewScale(
        previousSpan: Float,
        currentSpan: Float,
        currentScale: Float,
    ): Float {
        if (previousSpan == 0f) return currentScale
        return (currentScale * (currentSpan / previousSpan))
            .coerceIn(PlayerConfig.MIN_ZOOM_SCALE, PlayerConfig.MAX_ZOOM_SCALE)
    }
}
