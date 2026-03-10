package com.swipeplayer.ui

import android.net.Uri
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipeplayer.data.PlaybackHistory
import com.swipeplayer.data.VideoFile
import com.swipeplayer.data.VideoRepository
import com.swipeplayer.player.AudioFocusManager
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.player.VideoPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Main ViewModel. Single source of truth for [PlayerUiState].
 *
 * Responsibilities:
 *   - Load playlist from the URI received via the "Open with" intent
 *   - Orchestrate PlaybackHistory for swipe navigation
 *   - Drive VideoPlayerManager (prepare, swap, release)
 *   - React to AudioFocusManager events (pause/resume/duck)
 *   - Surface error states to the UI
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val playerManager: VideoPlayerManager,
    private val audioFocusManager: AudioFocusManager,
) : ViewModel(), AudioFocusManager.Listener {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Current ExoPlayer instance; observed by VideoSurface to attach the surface. */
    val currentPlayer get() = playerManager.currentPlayer

    private val history = PlaybackHistory()

    /** Current position-polling job; cancelled/restarted on play/pause. */
    private var positionPollingJob: Job? = null

    /** Pending codec-skip job (2-second delay before auto-skipping). */
    private var codecSkipJob: Job? = null

    /** Brightness bar visibility reset job. */
    private var hideBrightnessBarJob: Job? = null

    /** Volume bar visibility reset job. */
    private var hideVolumeBarJob: Job? = null

    /** Double-tap feedback clear job. */
    private var clearDoubleTapJob: Job? = null

    init {
        audioFocusManager.listener = this
        playerManager.onCodecFailure = { onCodecFailureDetected() }
    }

    // -------------------------------------------------------------------------
    // Intent handling
    // -------------------------------------------------------------------------

    /**
     * Entry point called by PlayerActivity when it receives an ACTION_VIEW intent.
     * Loads the playlist and starts playing the video at [uri].
     */
    fun onIntentReceived(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val playlist = videoRepository.listVideosInDirectory(uri)
                val startVideo = playlist.firstOrNull { it.uri == uri }
                    ?: videoRepository.resolveVideoFile(uri)
                    ?: playlist.first()

                val singleFileMode = playlist.size == 1 ||
                    (playlist.size == 1 && playlist.first().uri == uri &&
                        !videoRepository.isMediaStoreUri(uri) &&
                        !videoRepository.isSafUri(uri))

                history.init(startVideo, playlist)

                _uiState.update {
                    it.copy(
                        playlist = playlist,
                        currentVideo = startVideo,
                        previousVideo = null,
                        isSwipeEnabled = playlist.size > 1,
                        isLoading = false,
                        error = if (singleFileMode && playlist.size == 1)
                            PlayerError.ContentUriNoAccess else null,
                    )
                }

                startPlayback(startVideo)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = PlayerError.Generic(e.message ?: "Unknown error"),
                    )
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * Swipe up: advance to the next video.
     * Uses the pre-selected peek if available, otherwise picks a new random video.
     */
    fun onSwipeUp() {
        if (!_uiState.value.isSwipeEnabled) return
        viewModelScope.launch {
            val previous = history.current
            val next = history.navigateForward()
            switchToVideo(next, previousVideo = previous)
        }
    }

    /**
     * Swipe down: go back in history.
     * No-op at the start of history (UI shows a bounce animation instead).
     */
    fun onSwipeDown() {
        if (!_uiState.value.isSwipeEnabled) return
        viewModelScope.launch {
            val prev = history.navigateBack() ?: return@launch
            // Peek at the video before prev (for page 0 in the pager) without
            // permanently moving the history pointer.
            val beforePrev: VideoFile? = if (history.canGoBack) {
                val tmp = history.navigateBack()!!
                history.navigateForward()
                tmp
            } else null
            _uiState.update {
                it.copy(currentVideo = prev, previousVideo = beforePrev)
            }
            startPlayback(prev)
        }
    }

    // -------------------------------------------------------------------------
    // Playback controls
    // -------------------------------------------------------------------------

    fun onPlayPause() {
        val player = playerManager.currentPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            audioFocusManager.abandonFocus()
            stopPositionPolling()
            _uiState.update { it.copy(isPlaying = false) }
        } else {
            audioFocusManager.requestFocus()
            player.play()
            startPositionPolling()
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    fun onSeek(positionMs: Long) {
        playerManager.currentPlayer?.seekTo(positionMs)
        _uiState.update { it.copy(positionMs = positionMs) }
    }

    fun onSeekRelative(deltaMs: Long) {
        val player = playerManager.currentPlayer ?: return
        val newPos = (player.currentPosition + deltaMs).coerceIn(0L, player.duration)
        player.seekTo(newPos)
        _uiState.update { it.copy(positionMs = newPos) }
    }

    fun onSpeedChange(speed: Float) {
        playerManager.currentPlayer?.setPlaybackSpeed(speed)
        _uiState.update { it.copy(playbackSpeed = speed) }
    }

    // -------------------------------------------------------------------------
    // UI controls
    // -------------------------------------------------------------------------

    fun onToggleControls() {
        val nowVisible = !_uiState.value.controlsVisible
        _uiState.update { it.copy(controlsVisible = nowVisible) }
        // Auto-hide is handled by ControlsOverlay via LaunchedEffect
    }

    fun onZoomChange(scale: Float) {
        val clamped = scale.coerceIn(PlayerConfig.MIN_ZOOM_SCALE, PlayerConfig.MAX_ZOOM_SCALE)
        _uiState.update { it.copy(zoomScale = clamped) }
    }

    fun onDisplayModeChange() {
        val next = when (_uiState.value.displayMode) {
            DisplayMode.ADAPT      -> DisplayMode.FILL
            DisplayMode.FILL       -> DisplayMode.STRETCH
            DisplayMode.STRETCH    -> DisplayMode.NATIVE_100
            DisplayMode.NATIVE_100 -> DisplayMode.ADAPT
        }
        _uiState.update { it.copy(displayMode = next) }
    }

    fun onOrientationChange() {
        val next = when (_uiState.value.orientationMode) {
            OrientationMode.AUTO      -> OrientationMode.LANDSCAPE
            OrientationMode.LANDSCAPE -> OrientationMode.PORTRAIT
            OrientationMode.PORTRAIT  -> OrientationMode.AUTO
        }
        _uiState.update { it.copy(orientationMode = next) }
    }

    fun onBrightnessChange(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness.coerceIn(0f, 1f)) }
    }

    /** Incremental brightness update from gesture (positive = drag up = brighter). */
    fun onBrightnessDelta(delta: Float) {
        val cur = _uiState.value.brightness.let { if (it < 0f) 0.5f else it }
        _uiState.update { it.copy(brightness = (cur + delta).coerceIn(0f, 1f), showBrightnessBar = true) }
        hideBrightnessBarJob?.cancel()
        hideBrightnessBarJob = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(showBrightnessBar = false) }
        }
    }

    fun onVolumeChange(volume: Float) {
        _uiState.update { it.copy(volume = volume.coerceIn(0f, 1f)) }
    }

    /** Incremental volume update from gesture (positive = drag up = louder). */
    fun onVolumeDelta(delta: Float) {
        val cur = _uiState.value.volume
        _uiState.update { it.copy(volume = (cur + delta).coerceIn(0f, 1f), showVolumeBar = true) }
        hideVolumeBarJob?.cancel()
        hideVolumeBarJob = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(showVolumeBar = false) }
        }
    }

    /** Shows the double-tap feedback animation and clears it after 600ms. */
    fun onDoubleTap(side: TapSide) {
        _uiState.update { it.copy(doubleTapSide = side) }
        clearDoubleTapJob?.cancel()
        clearDoubleTapJob = viewModelScope.launch {
            delay(600L)
            _uiState.update { it.copy(doubleTapSide = null) }
        }
    }

    // -------------------------------------------------------------------------
    // AudioFocusManager.Listener
    // -------------------------------------------------------------------------

    override fun onPause() {
        playerManager.currentPlayer?.pause()
        stopPositionPolling()
        _uiState.update { it.copy(isPlaying = false) }
    }

    override fun onResume() {
        playerManager.currentPlayer?.play()
        startPositionPolling()
        _uiState.update { it.copy(isPlaying = true) }
    }

    override fun onDuck() {
        playerManager.currentPlayer?.volume = 0.3f
    }

    override fun onUnduck() {
        playerManager.currentPlayer?.volume = 1.0f
    }

    // -------------------------------------------------------------------------
    // Lifecycle hooks (called by PlayerActivity)
    // -------------------------------------------------------------------------

    fun onActivityStart() {
        // Nothing required here; audio focus is requested at playback start
    }

    fun onActivityStop() {
        // Pause if playing; audio focus is abandoned
        if (_uiState.value.isPlaying) {
            playerManager.currentPlayer?.pause()
            audioFocusManager.abandonFocus()
            stopPositionPolling()
            _uiState.update { it.copy(isPlaying = false) }
        }
    }

    fun onActivityDestroy() {
        viewModelScope.launch {
            playerManager.releaseAll()
            playerManager.close()
        }
    }

    // -------------------------------------------------------------------------
    // Surface attachment (called by VideoSurface composable)
    // -------------------------------------------------------------------------

    fun attachSurface(surfaceView: SurfaceView) {
        playerManager.currentPlayer?.let {
            playerManager.attachSurface(it, surfaceView)
        }
    }

    fun detachSurface(surfaceView: SurfaceView) {
        playerManager.currentPlayer?.let {
            playerManager.detachSurface(it, surfaceView)
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private suspend fun startPlayback(video: VideoFile) {
        val oldPlayer = playerManager.currentPlayer
        // preparePlayer sets playerManager.currentPlayer = newPlayer when preloadOnly=false
        val newPlayer = playerManager.preparePlayer(video, preloadOnly = false)
        // Release the old player asynchronously (oldPlayer != newPlayer after preparePlayer)
        oldPlayer?.let {
            if (it !== newPlayer) viewModelScope.launch { playerManager.releasePlayer(it, null) }
        }
        triggerPeekNext()
        audioFocusManager.requestFocus()
        startPositionPolling()
        _uiState.update { it.copy(isPlaying = true) }
    }

    private suspend fun switchToVideo(video: VideoFile, previousVideo: VideoFile?) {
        _uiState.update {
            it.copy(
                currentVideo = video,
                previousVideo = previousVideo,
            )
        }
        startPlayback(video)
    }

    /** Pre-selects the next video and starts buffering it in nextPlayer. */
    private fun triggerPeekNext() {
        val playlist = _uiState.value.playlist
        if (playlist.size <= 1) return
        viewModelScope.launch {
            val next = history.peekNext()
            playerManager.preloadNext(next)
        }
    }

    /** Polls player position every 500ms while playing. */
    private fun startPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = viewModelScope.launch {
            while (true) {
                val player = playerManager.currentPlayer
                if (player != null) {
                    _uiState.update {
                        it.copy(
                            positionMs = player.currentPosition,
                            durationMs = player.duration.coerceAtLeast(0L),
                            bufferedPositionMs = player.bufferedPosition,
                            isPlaying = player.isPlaying,
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }


    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    private fun onCodecFailureDetected() {
        _uiState.update { it.copy(error = PlayerError.CodecNotSupported) }
        codecSkipJob?.cancel()
        codecSkipJob = viewModelScope.launch {
            delay(PlayerConfig.CODEC_FAILURE_SKIP_DELAY_MS)
            _uiState.update { it.copy(error = null) }
            if (_uiState.value.isSwipeEnabled) onSwipeUp()
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionPollingJob?.cancel()
        codecSkipJob?.cancel()
        hideBrightnessBarJob?.cancel()
        hideVolumeBarJob?.cancel()
        clearDoubleTapJob?.cancel()
        audioFocusManager.listener = null
        playerManager.onCodecFailure = null
        playerManager.close()
    }
}
