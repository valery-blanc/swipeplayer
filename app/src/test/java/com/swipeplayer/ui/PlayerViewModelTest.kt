package com.swipeplayer.ui

import android.net.Uri
import com.swipeplayer.data.VideoFile
import com.swipeplayer.data.VideoRepository
import com.swipeplayer.player.AudioFocusManager
import com.swipeplayer.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // -- Mocks ----------------------------------------------------------------

    private val mockRepo: VideoRepository = mock()
    private val mockPlayerManager: VideoPlayerManager = mock()
    private val mockAudioFocusManager: AudioFocusManager = mock {
        on { listener } doReturn null
    }

    private fun video(name: String) = VideoFile(
        uri = mock<Uri>(),
        name = name,
        path = "",
        duration = 60_000L,
    )

    private val v1 = video("v1.mp4")
    private val v2 = video("v2.mp4")
    private val v3 = video("v3.mp4")
    private val playlist = listOf(v1, v2, v3)

    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = PlayerViewModel(mockRepo, mockPlayerManager, mockAudioFocusManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has empty playlist and no video`() {
        val state = viewModel.uiState.value
        assertTrue(state.playlist.isEmpty())
        assertNull(state.currentVideo)
        assertFalse(state.isPlaying)
    }

    // -------------------------------------------------------------------------
    // UI toggles (pure state, no Android dependencies)
    // -------------------------------------------------------------------------

    @Test
    fun `onToggleControls flips controlsVisible`() {
        // onToggleControls is synchronous; state updates happen immediately.
        // We avoid advanceUntilIdle() here because it would trigger the
        // 4-second scheduleHideControls delay and flip the state back.
        assertTrue(viewModel.uiState.value.controlsVisible)
        viewModel.onToggleControls()
        assertFalse(viewModel.uiState.value.controlsVisible)
        viewModel.onToggleControls()
        assertTrue(viewModel.uiState.value.controlsVisible)
    }

    @Test
    fun `onZoomChange clamps to PlayerConfig bounds`() {
        viewModel.onZoomChange(10f)
        assertEquals(4f, viewModel.uiState.value.zoomScale)
        viewModel.onZoomChange(0.1f)
        assertEquals(1f, viewModel.uiState.value.zoomScale)
        viewModel.onZoomChange(2f)
        assertEquals(2f, viewModel.uiState.value.zoomScale)
    }

    @Test
    fun `onDisplayModeChange cycles through all modes`() {
        assertEquals(DisplayMode.ADAPT, viewModel.uiState.value.displayMode)
        viewModel.onDisplayModeChange()
        assertEquals(DisplayMode.FILL, viewModel.uiState.value.displayMode)
        viewModel.onDisplayModeChange()
        assertEquals(DisplayMode.STRETCH, viewModel.uiState.value.displayMode)
        viewModel.onDisplayModeChange()
        assertEquals(DisplayMode.NATIVE_100, viewModel.uiState.value.displayMode)
        viewModel.onDisplayModeChange()
        assertEquals(DisplayMode.ADAPT, viewModel.uiState.value.displayMode)
    }

    @Test
    fun `onOrientationChange cycles through all modes`() {
        assertEquals(OrientationMode.AUTO, viewModel.uiState.value.orientationMode)
        viewModel.onOrientationChange()
        assertEquals(OrientationMode.LANDSCAPE, viewModel.uiState.value.orientationMode)
        viewModel.onOrientationChange()
        assertEquals(OrientationMode.PORTRAIT, viewModel.uiState.value.orientationMode)
        viewModel.onOrientationChange()
        assertEquals(OrientationMode.AUTO, viewModel.uiState.value.orientationMode)
    }

    @Test
    fun `onBrightnessChange clamps to 0f-1f`() {
        viewModel.onBrightnessChange(1.5f)
        assertEquals(1f, viewModel.uiState.value.brightness)
        viewModel.onBrightnessChange(-0.1f)
        assertEquals(0f, viewModel.uiState.value.brightness)
        viewModel.onBrightnessChange(0.5f)
        assertEquals(0.5f, viewModel.uiState.value.brightness)
    }

    @Test
    fun `onVolumeChange clamps to 0f-1f`() {
        viewModel.onVolumeChange(2f)
        assertEquals(1f, viewModel.uiState.value.volume)
        viewModel.onVolumeChange(-1f)
        assertEquals(0f, viewModel.uiState.value.volume)
    }

    // -------------------------------------------------------------------------
    // AudioFocusManager.Listener
    // -------------------------------------------------------------------------

    @Test
    fun `onDuck sets player volume to 30 percent`() {
        val mockPlayer = mock<androidx.media3.exoplayer.ExoPlayer>()
        whenever(mockPlayerManager.currentPlayer).thenReturn(mockPlayer)
        viewModel.onDuck()
        verify(mockPlayer).volume = 0.3f
    }

    @Test
    fun `onUnduck restores player volume to 100 percent`() {
        val mockPlayer = mock<androidx.media3.exoplayer.ExoPlayer>()
        whenever(mockPlayerManager.currentPlayer).thenReturn(mockPlayer)
        viewModel.onUnduck()
        verify(mockPlayer).volume = 1.0f
    }

    // -------------------------------------------------------------------------
    // BUG-001 regression — onSwipeUp before onIntentReceived must not crash
    // -------------------------------------------------------------------------

    @Test
    fun `initial state has swipe disabled (BUG-001 regression)`() {
        // isSwipeEnabled must default to false so that onSwipeUp called before
        // onIntentReceived (and therefore before history.init) is a no-op.
        assertFalse(viewModel.uiState.value.isSwipeEnabled)
    }

    @Test
    fun `onSwipeUp before onIntentReceived does not crash`() = runTest {
        // PlaybackHistory is uninitialised at this point.
        // The guard !isSwipeEnabled must prevent navigateForward() from being called.
        viewModel.onSwipeUp()
        testDispatcher.scheduler.advanceUntilIdle()
        // If we reach here without IllegalStateException the bug is fixed.
        assertFalse(viewModel.uiState.value.isSwipeEnabled)
    }

    @Test
    fun `onSwipeDown before onIntentReceived does not crash`() = runTest {
        viewModel.onSwipeDown()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSwipeEnabled)
    }

    // -------------------------------------------------------------------------
    // onSwipeUp / onSwipeDown when swipe is disabled
    // -------------------------------------------------------------------------

    @Test
    fun `onSwipeUp is no-op when isSwipeEnabled is false`() = runTest {
        // isSwipeEnabled is already false by default; just verify the guard.
        assertFalse(viewModel.uiState.value.isSwipeEnabled)
        viewModel.onSwipeUp()
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isSwipeEnabled)
    }
}
