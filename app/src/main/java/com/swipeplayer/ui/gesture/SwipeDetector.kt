package com.swipeplayer.ui.gesture

import com.swipeplayer.player.PlayerConfig
import kotlin.math.absoluteValue

/** Direction of a completed vertical swipe gesture. */
enum class SwipeResult { UP, DOWN }

/**
 * Classifies a vertical pointer movement as a confirmed swipe or null.
 *
 * A swipe is confirmed when BOTH conditions are met (spec §7):
 *   1. |deltaY| >= [minDistancePx]
 *   2. |velocityY| >= [PlayerConfig.SWIPE_MIN_VELOCITY_DP] * density (dp/s)
 *
 * Direction: negative deltaY (finger moved up) = SwipeResult.UP (next video).
 *
 * [isHorizontalIntent] can be called early in a gesture to decide whether to
 * cancel swipe detection and hand control to the seekbar.
 */
class SwipeDetector(
    private val minDistancePx: Float,
    /** Screen density (dp to px ratio), used to convert velocity threshold. */
    private val density: Float = 1f,
) {

    /**
     * Returns UP or DOWN if the vertical travel exceeds [minDistancePx] AND
     * the fling velocity meets the minimum threshold.
     * Returns null otherwise.
     *
     * [startY] and [currentY] are absolute screen coordinates (pixels).
     * [velocityY] is in pixels/second (from VelocityTracker).
     */
    fun detect(startY: Float, currentY: Float, velocityY: Float): SwipeResult? {
        val delta = currentY - startY
        val minVelocityPxPerSec = PlayerConfig.SWIPE_MIN_VELOCITY_DP * density
        return when {
            delta <= -minDistancePx && velocityY.absoluteValue >= minVelocityPxPerSec -> SwipeResult.UP
            delta >= minDistancePx  && velocityY.absoluteValue >= minVelocityPxPerSec -> SwipeResult.DOWN
            else -> null
        }
    }

    /**
     * Returns true when the movement is predominantly horizontal — a signal
     * that the user is interacting with the seekbar, not swiping videos.
     *
     * Threshold: |deltaX| > |deltaY| * 1.5
     */
    fun isHorizontalIntent(deltaX: Float, deltaY: Float): Boolean =
        deltaX.absoluteValue > deltaY.absoluteValue * 1.5f
}
