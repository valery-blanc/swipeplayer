package com.swipeplayer.ui.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDetectorTest {

    // 80dp @ 2x density = 160px
    private val detector = SwipeDetector(minDistancePx = 160f)

    // -------------------------------------------------------------------------
    // detect()
    // -------------------------------------------------------------------------

    @Test
    fun `swipe up detected when delta exceeds threshold`() {
        val result = detector.detect(startY = 500f, currentY = 300f, velocityY = -1000f)
        assertEquals(SwipeResult.UP, result)
    }

    @Test
    fun `swipe down detected when delta exceeds threshold`() {
        val result = detector.detect(startY = 300f, currentY = 500f, velocityY = 1000f)
        assertEquals(SwipeResult.DOWN, result)
    }

    @Test
    fun `null when delta below threshold upward`() {
        val result = detector.detect(startY = 500f, currentY = 400f, velocityY = -500f)
        assertNull(result)
    }

    @Test
    fun `null when delta below threshold downward`() {
        val result = detector.detect(startY = 400f, currentY = 450f, velocityY = 500f)
        assertNull(result)
    }

    @Test
    fun `null when no movement`() {
        assertNull(detector.detect(startY = 400f, currentY = 400f, velocityY = 0f))
    }

    @Test
    fun `exactly at threshold upward returns UP`() {
        val result = detector.detect(startY = 500f, currentY = 340f, velocityY = -800f)
        assertEquals(SwipeResult.UP, result)
    }

    @Test
    fun `exactly at threshold downward returns DOWN`() {
        val result = detector.detect(startY = 300f, currentY = 460f, velocityY = 800f)
        assertEquals(SwipeResult.DOWN, result)
    }

    // -------------------------------------------------------------------------
    // isHorizontalIntent()
    // -------------------------------------------------------------------------

    @Test
    fun `horizontal intent true when deltaX dominates`() {
        assertTrue(detector.isHorizontalIntent(deltaX = 100f, deltaY = 20f))
    }

    @Test
    fun `horizontal intent false when deltaY dominates`() {
        assertFalse(detector.isHorizontalIntent(deltaX = 10f, deltaY = 80f))
    }

    @Test
    fun `horizontal intent false for pure vertical`() {
        assertFalse(detector.isHorizontalIntent(deltaX = 0f, deltaY = 100f))
    }

    @Test
    fun `horizontal intent true for pure horizontal`() {
        assertTrue(detector.isHorizontalIntent(deltaX = 100f, deltaY = 0f))
    }

    @Test
    fun `horizontal intent false at 45 degrees`() {
        // |50| > |50| * 1.5 = 75 -> false
        assertFalse(detector.isHorizontalIntent(deltaX = 50f, deltaY = 50f))
    }
}
