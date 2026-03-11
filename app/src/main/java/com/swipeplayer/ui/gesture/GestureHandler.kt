package com.swipeplayer.ui.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.swipeplayer.player.PlayerConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/** Screen zone determined by the X position of the first touch point. */
enum class GestureZone { LEFT, CENTER, RIGHT }

/**
 * Classifies an X coordinate into a [GestureZone]:
 *   Left 15%   -> LEFT   (brightness control)
 *   Right 15%  -> RIGHT  (volume control)
 *   Center 70% -> CENTER (navigation, zoom, tap)
 */
fun classifyZone(x: Float, screenWidth: Float): GestureZone = when {
    x < screenWidth * PlayerConfig.LEFT_ZONE_FRACTION  -> GestureZone.LEFT
    x > screenWidth * PlayerConfig.RIGHT_ZONE_FRACTION -> GestureZone.RIGHT
    else                                                -> GestureZone.CENTER
}

/**
 * Single-pointerInput Modifier extension handling ALL touch gestures.
 * Uses [awaitEachGesture] inside a [coroutineScope] so that the delayed
 * single-tap job can run concurrently with the gesture detection loop.
 *
 * Routing:
 *   2+ fingers (any zone) -> pinch zoom
 *   LEFT zone             -> vertical drag = brightness delta (incremental)
 *   RIGHT zone            -> vertical drag = volume delta (incremental)
 *   CENTER zone:
 *     horizontal first motion  -> seekbar mode (swipe cancelled)
 *     zoomScale > 1x           -> navigation swipe disabled
 *     drag >= 80dp             -> onSwipeUp / onSwipeDown
 *     short tap (< 400ms):
 *       second tap within 200ms -> double-tap seek
 *       otherwise               -> delayed single tap (toggle controls)
 */
fun Modifier.gestureHandler(
    screenWidthPx: Float,
    screenHeightPx: Float,
    zoomScale: () -> Float,
    isSwipeEnabled: Boolean,
    canSwipeDown: Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onZoom: (Float) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
): Modifier = this.pointerInput(isSwipeEnabled, canSwipeDown, screenWidthPx, screenHeightPx) {

    val minSwipePx = PlayerConfig.SWIPE_MIN_DP * density
    val swipeDetector = SwipeDetector(minSwipePx)
    val pinchHandler = PinchZoomHandler()

    // coroutineScope lets us launch the delayed single-tap job while
    // awaitEachGesture continues listening for the next gesture.
    coroutineScope {
        var lastTapTimeMs = 0L
        var pendingSingleTapJob: Job? = null

        awaitEachGesture {
            val velocityTracker = VelocityTracker()

            // --- Gesture start ---
            val firstDown = awaitFirstDown(requireUnconsumed = true)
            val startPos = firstDown.position
            val startTimeMs = System.currentTimeMillis()
            val zone = classifyZone(startPos.x, screenWidthPx)

            velocityTracker.addPosition(startTimeMs, startPos)

            var previousSpan = 0f
            var isPinch = false
            var isDragging = false
            var isHorizontal = false
            var totalDelta = Offset.Zero
            var lastPos = startPos

            // --- Track until all fingers lifted ---
            while (true) {
                val event = awaitPointerEvent()
                val active = event.changes.filter { it.pressed }
                if (active.isEmpty()) break

                // Pinch (2+ fingers)
                if (active.size >= 2) {
                    isPinch = true
                    val p1 = active[0].position
                    val p2 = active[1].position
                    val span = (p1 - p2).getDistance()
                    if (previousSpan == 0f) previousSpan = span
                    onZoom(pinchHandler.calculateNewScale(previousSpan, span, zoomScale()))
                    previousSpan = span
                    active.forEach { it.consume() }
                    continue
                }

                if (isPinch) continue   // wait for full release after pinch

                val change = active.first()
                val pos = change.position
                totalDelta = pos - startPos
                velocityTracker.addPosition(System.currentTimeMillis(), pos)

                if (!isDragging && !isHorizontal) {
                    isHorizontal = swipeDetector.isHorizontalIntent(totalDelta.x, totalDelta.y)
                }

                when (zone) {
                    GestureZone.LEFT -> {
                        if (totalDelta.y.absoluteValue > 8f) {
                            onBrightnessDelta(-(pos.y - lastPos.y) / screenHeightPx)
                            lastPos = pos
                            change.consume()
                        }
                    }
                    GestureZone.RIGHT -> {
                        if (totalDelta.y.absoluteValue > 8f) {
                            onVolumeDelta(-(pos.y - lastPos.y) / screenHeightPx)
                            lastPos = pos
                            change.consume()
                        }
                    }
                    GestureZone.CENTER -> {
                        if (!isHorizontal && totalDelta.y.absoluteValue >= minSwipePx) {
                            isDragging = true
                        }
                        if (!isHorizontal) change.consume()
                    }
                }
            }

            // --- Dispatch result ---
            if (isPinch) return@awaitEachGesture

            val elapsed = System.currentTimeMillis() - startTimeMs
            val isTap = !isDragging && !isHorizontal && elapsed < 400L

            when (zone) {
                GestureZone.CENTER -> when {
                    isTap -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTimeMs < PlayerConfig.DOUBLE_TAP_WINDOW_MS) {
                            pendingSingleTapJob?.cancel()
                            if (startPos.x < screenWidthPx / 2) onDoubleTapLeft()
                            else onDoubleTapRight()
                            lastTapTimeMs = 0L
                        } else {
                            lastTapTimeMs = now
                            pendingSingleTapJob?.cancel()
                            // launch runs concurrently in the outer coroutineScope
                            pendingSingleTapJob = launch {
                                delay(PlayerConfig.DOUBLE_TAP_WINDOW_MS)
                                onTap()
                            }
                        }
                    }
                    isDragging && !isHorizontal && isSwipeEnabled -> {
                        val result = swipeDetector.detect(
                            startY = startPos.y,
                            currentY = startPos.y + totalDelta.y,
                            velocityY = velocityTracker.calculateVelocity().y,
                        )
                        when (result) {
                            SwipeResult.UP   -> onSwipeUp()
                            SwipeResult.DOWN -> if (canSwipeDown) onSwipeDown()
                            null             -> Unit
                        }
                    }
                }
                GestureZone.LEFT, GestureZone.RIGHT -> Unit
            }
        }
    }
}
