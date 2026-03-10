package com.swipeplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerUiStateTest {

    private val state = PlayerUiState()

    @Test
    fun `default playlist is empty`() {
        assertTrue(state.playlist.isEmpty())
        assertNull(state.currentVideo)
        assertNull(state.previousVideo)
    }

    @Test
    fun `swipe is enabled by default`() {
        assertTrue(state.isSwipeEnabled)
    }

    @Test
    fun `default playback state is paused at position 0`() {
        assertFalse(state.isPlaying)
        assertEquals(1f, state.playbackSpeed)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
    }

    @Test
    fun `controls are visible by default`() {
        assertTrue(state.controlsVisible)
    }

    @Test
    fun `default zoom is 1x (no zoom)`() {
        assertEquals(1f, state.zoomScale)
    }

    @Test
    fun `default display mode is ADAPT`() {
        assertEquals(DisplayMode.ADAPT, state.displayMode)
    }

    @Test
    fun `default orientation is AUTO`() {
        assertEquals(OrientationMode.AUTO, state.orientationMode)
    }

    @Test
    fun `default brightness is -1 (system brightness)`() {
        assertEquals(-1f, state.brightness)
    }

    @Test
    fun `default volume is 1 (full)`() {
        assertEquals(1f, state.volume)
    }

    @Test
    fun `no error by default`() {
        assertNull(state.error)
        assertFalse(state.isLoading)
    }

    @Test
    fun `PlayerError subtypes are distinct`() {
        val e1: PlayerError = PlayerError.CodecNotSupported
        val e2: PlayerError = PlayerError.FileNotFound
        val e3: PlayerError = PlayerError.ContentUriNoAccess
        val e4: PlayerError = PlayerError.Generic("oops")
        // All four are different
        assertTrue(e1 != e2 && e2 != e3 && e3 != e4)
    }

    @Test
    fun `DisplayMode cycle order`() {
        val values = DisplayMode.entries
        assertEquals(DisplayMode.ADAPT,      values[0])
        assertEquals(DisplayMode.FILL,       values[1])
        assertEquals(DisplayMode.STRETCH,    values[2])
        assertEquals(DisplayMode.NATIVE_100, values[3])
    }

    @Test
    fun `OrientationMode cycle order`() {
        val values = OrientationMode.entries
        assertEquals(OrientationMode.AUTO,      values[0])
        assertEquals(OrientationMode.LANDSCAPE, values[1])
        assertEquals(OrientationMode.PORTRAIT,  values[2])
    }
}
