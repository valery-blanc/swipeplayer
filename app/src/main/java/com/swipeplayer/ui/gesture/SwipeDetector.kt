package com.swipeplayer.ui.gesture

import kotlin.math.absoluteValue

/** Direction of a completed vertical swipe gesture. */
enum class SwipeResult { UP, DOWN }

/**
 * Classifies a vertical pointer movement as a confirmed swipe or null.
 *
 * A swipe is confirmed when:
 *   |deltaY| >= [minDistancePx]
 *
 * Direction: negative deltaY (finger moved up) = SwipeResult.UP (next video).
 *
 * [isHorizontalIntent] can be called early in a gesture to decide whether to
 * cancel swipe detection and hand control to the seekbar.
 */
class SwipeDetector(private val minDistancePx: Float) {

    /**
     * Returns UP or DOWN if the vertical travel exceeds [minDistancePx],
     * null otherwise.
     *
     * [startY] and [currentY] are absolute screen coordinates.
     * [velocityY] is currently unused but reserved for future velocity gating.
     */
    fun detect(startY: Float, currentY: Float, @Suppress("UNUSED_PARAMETER") velocityY: Float): SwipeResult? {
        val delta = currentY - startY
        return when {
            delta <= -minDistancePx -> SwipeResult.UP
            delta >= minDistancePx  -> SwipeResult.DOWN
            else                    -> null
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
