package com.swipeplayer.ui.gesture

import org.junit.Assert.assertEquals
import org.junit.Test

class PinchZoomHandlerTest {

    private val handler = PinchZoomHandler()
    private val eps = 0.001f

    @Test
    fun `span doubling from 1x gives 2x`() {
        assertEquals(2f, handler.calculateNewScale(100f, 200f, 1f), eps)
    }

    @Test
    fun `span halving from 1x stays at 1x (min clamp)`() {
        assertEquals(1f, handler.calculateNewScale(100f, 50f, 1f), eps)
    }

    @Test
    fun `span doubling from 3x gives 6x (no low max clamp)`() {
        assertEquals(6f, handler.calculateNewScale(100f, 200f, 3f), eps)
    }

    @Test
    fun `equal spans return current scale unchanged`() {
        assertEquals(2f, handler.calculateNewScale(100f, 100f, 2f), eps)
    }

    @Test
    fun `zero previousSpan returns current scale (no div by zero)`() {
        assertEquals(2f, handler.calculateNewScale(0f, 200f, 2f), eps)
    }

    @Test
    fun `span tripling from 1x clamped to 3x (within bounds)`() {
        assertEquals(3f, handler.calculateNewScale(100f, 300f, 1f), eps)
    }

    @Test
    fun `large span ratio clamped at 50x (max)`() {
        assertEquals(50f, handler.calculateNewScale(10f, 10000f, 1f), eps)
    }

    @Test
    fun `pinch-in from 2x toward 1x`() {
        // 2x * (75/100) = 1.5x
        assertEquals(1.5f, handler.calculateNewScale(100f, 75f, 2f), eps)
    }
}
