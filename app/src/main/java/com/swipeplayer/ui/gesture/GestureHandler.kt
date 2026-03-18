package com.swipeplayer.ui.gesture

import android.os.SystemClock
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
 *     drag >= 10px             -> onVideoDragUpdate(dy) called continuously (FEAT-009)
 *     release >= 25% screen OR high velocity -> onSwipeUp / onSwipeDown
 *     release < 25% screen     -> onVideoDragCancel (elastic spring back)
 *     short tap (< 400ms):
 *       second tap within 200ms -> double-tap seek
 *       otherwise               -> delayed single tap (toggle controls)
 *
 * CRO-020: [canSwipeDown] is a lambda so it is NOT a key of [pointerInput] and
 * the gesture handler is not recreated on every swipe (which was resetting
 * [lastTapTimeMs] and breaking double-tap detection).
 */
fun Modifier.gestureHandler(
    screenWidthPx: Float,
    screenHeightPx: Float,
    zoomScale: () -> Float,
    isSwipeEnabled: Boolean,
    canSwipeDown: () -> Boolean,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onTap: () -> Unit,
    onDoubleTapLeft: () -> Unit,
    onDoubleTapRight: () -> Unit,
    onZoom: (Float) -> Unit,
    onBrightnessDelta: (Float) -> Unit,
    onVolumeDelta: (Float) -> Unit,
    onHorizontalSeekStart: () -> Unit = {},
    onHorizontalSeekUpdate: (deltaX: Float, screenWidthPx: Float) -> Unit = { _, _ -> },
    onHorizontalSeekEnd: () -> Unit = {},
    onHorizontalSeekCancel: () -> Unit = {},
    // FEAT-009: real-time drag progress for TikTok-style animation
    onVideoDragUpdate: (dy: Float) -> Unit = {},
    onVideoDragCancel: () -> Unit = {},
// CRO-020: canSwipeDown removed from keys so handler is not recreated on every swipe
): Modifier = this.pointerInput(isSwipeEnabled, screenWidthPx, screenHeightPx) {

    val minSwipePx = PlayerConfig.SWIPE_MIN_DP * density
    // CRO-021: 20dp dead zone for side brightness/volume gestures (was 8px hardcoded)
    val sideDeadZonePx = 20f * density
    val swipeDetector = SwipeDetector(minSwipePx, density)
    val pinchHandler = PinchZoomHandler()

    // coroutineScope lets us launch the delayed single-tap job while
    // awaitEachGesture continues listening for the next gesture.
    coroutineScope {
        // CRO-002: use uptimeMillis for consistent timing with VelocityTracker
        var lastTapTimeMs = 0L
        var pendingSingleTapJob: Job? = null

        awaitEachGesture {
            val velocityTracker = VelocityTracker()

            // --- Gesture start ---
            val firstDown = awaitFirstDown(requireUnconsumed = true)
            val startPos = firstDown.position
            // CRO-002: uptimeMillis is the correct clock for VelocityTracker and tap timing
            val startTimeMs = SystemClock.uptimeMillis()
            val zone = classifyZone(startPos.x, screenWidthPx)

            velocityTracker.addPosition(startTimeMs, startPos)

            var previousSpan = 0f
            var isPinch = false
            var isDragging = false
            var isHorizontal = false
            var isSeekingHorizontal = false
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
                // CRO-002: uptimeMillis matches the Android input event timestamp reference
                velocityTracker.addPosition(SystemClock.uptimeMillis(), pos)

                if (!isDragging && !isHorizontal) {
                    isHorizontal = swipeDetector.isHorizontalIntent(totalDelta.x, totalDelta.y)
                }

                when (zone) {
                    GestureZone.LEFT -> {
                        // CRO-021: 20dp dead zone instead of 8px to avoid accidental activation
                        if (totalDelta.y.absoluteValue > sideDeadZonePx) {
                            onBrightnessDelta(-(pos.y - lastPos.y) / screenHeightPx)
                            lastPos = pos
                            change.consume()
                        }
                    }
                    GestureZone.RIGHT -> {
                        // CRO-021: 20dp dead zone instead of 8px to avoid accidental activation
                        if (totalDelta.y.absoluteValue > sideDeadZonePx) {
                            onVolumeDelta(-(pos.y - lastPos.y) / screenHeightPx)
                            lastPos = pos
                            change.consume()
                        }
                    }
                    GestureZone.CENTER -> {
                        if (isSeekingHorizontal) {
                            onHorizontalSeekUpdate(totalDelta.x, screenWidthPx)
                            change.consume()
                        } else if (isHorizontal) {
                            // Horizontal intent confirmed — cancel any in-progress drag
                            if (isDragging) {
                                isDragging = false
                                onVideoDragCancel()
                            }
                            isSeekingHorizontal = true
                            onHorizontalSeekStart()
                            onHorizontalSeekUpdate(totalDelta.x, screenWidthPx)
                            change.consume()
                        } else {
                            // FEAT-009: start drag animation after small dead zone (10px),
                            // well before the 80dp commit threshold.
                            if (!isDragging &&
                                totalDelta.y.absoluteValue >= PlayerConfig.SWIPE_DRAG_START_PX) {
                                isDragging = true
                            }
                            if (isDragging) {
                                onVideoDragUpdate(totalDelta.y)
                            }
                            change.consume()
                        }
                    }
                }
            }

            // --- Dispatch result ---
            if (isPinch) {
                if (isSeekingHorizontal) onHorizontalSeekCancel()
                // FEAT-009: cancel any in-progress drag animation if pinch took over
                if (isDragging && zone == GestureZone.CENTER) onVideoDragCancel()
                return@awaitEachGesture
            }

            if (isSeekingHorizontal) {
                onHorizontalSeekEnd()
                return@awaitEachGesture
            }

            // CRO-002: uptimeMillis for elapsed tap duration
            val elapsed = SystemClock.uptimeMillis() - startTimeMs
            val isTap = !isDragging && !isHorizontal && elapsed < 400L

            when (zone) {
                GestureZone.CENTER -> when {
                    isTap -> {
                        // CRO-002: uptimeMillis for double-tap window measurement
                        val now = SystemClock.uptimeMillis()
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
                    isDragging && !isHorizontal -> {
                        if (isSwipeEnabled) {
                            // FEAT-009: commit threshold = 25% of screen height OR high velocity
                            val velocity = velocityTracker.calculateVelocity().y
                            val commitDistPx = screenHeightPx * PlayerConfig.SWIPE_COMMIT_FRACTION
                            val commitVelPx = PlayerConfig.SWIPE_COMMIT_VELOCITY_DP * density
                            val committed = totalDelta.y.absoluteValue >= commitDistPx
                                || velocity.absoluteValue >= commitVelPx
                            if (committed) {
                                if (totalDelta.y < 0) onSwipeUp()
                                // CRO-020: canSwipeDown is a lambda
                                else if (canSwipeDown()) onSwipeDown()
                                else onVideoDragCancel()
                            } else {
                                onVideoDragCancel()
                            }
                        } else {
                            onVideoDragCancel()
                        }
                    }
                }
                GestureZone.LEFT, GestureZone.RIGHT -> Unit
            }
        }
    }
}
