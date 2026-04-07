package com.swipeplayer.ui

import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlayer
import com.swipeplayer.data.PlaybackHistory
import com.swipeplayer.data.VideoFile
import com.swipeplayer.data.VideoRepository
import com.swipeplayer.data.VideoStateStore
import com.swipeplayer.player.AudioFocusManager
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.player.VideoPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
private const val TAG = "SwipePlayer"

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val videoRepository: VideoRepository,
    private val playerManager: VideoPlayerManager,
    private val audioFocusManager: AudioFocusManager,
    private val videoStateStore: VideoStateStore,
) : ViewModel(), AudioFocusManager.Listener {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    /** Current ExoPlayer instance; observed by VideoSurface to attach the surface. */
    val currentPlayer get() = playerManager.currentPlayer

    /**
     * Next ExoPlayer instance (preloaded, paused on first frame).
     * Null when no preload is active.
     */
    val nextPlayer get() = playerManager.nextPlayer
    val prevPlayer get() = playerManager.prevPlayer

    /**
     * StateFlow of the preloaded next ExoPlayer.
     * Observed by PlayerScreen ping-pong to update the off-screen surface layer
     * once a new video is buffered (FEAT-009).
     */
    val nextPlayerState: StateFlow<ExoPlayer?> = playerManager.nextPlayerState

    /**
     * StateFlow of the preloaded previous ExoPlayer.
     * Symmetric to [nextPlayerState] — enables instant swipe-DOWN transitions.
     */
    val prevPlayerState: StateFlow<ExoPlayer?> = playerManager.prevPlayerState

    /** Flow of the current ExoPlayer — used by PlayerActivity to update MediaSession. */
    val currentPlayerState: StateFlow<ExoPlayer?> = playerManager.currentPlayerState

    /** One-shot toast messages for the UI to display via Toast.makeText. */
    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    /**
     * BUG-027: emits when the current video ends and swipe is enabled.
     * Observed by PlayerScreen to trigger the TikTok swipe-up animation,
     * exactly as if the user had swiped manually.
     */
    private val _autoSwipeUpEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoSwipeUpEvent: SharedFlow<Unit> = _autoSwipeUpEvent.asSharedFlow()

    private val history = PlaybackHistory()

    /** FEAT-018: stored to enable lazy sibling-directory scan for PARENT_RANDOM. */
    private var intentUri: android.net.Uri? = null

    /** FEAT-018: job for async sibling scan; cancelled before each new scan. */
    private var siblingLoadJob: Job? = null

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

    /** CRO-010: pending preload job; cancelled before each new preload to avoid races. */
    private var peekJob: Job? = null

    /** Pending prev-preload job; cancelled before each new preload. */
    private var peekPrevJob: Job? = null

    /**
     * CRO-009: serializes all history navigation operations.
     * PlaybackHistory is not thread-safe; rapid swipes can interleave at suspend
     * points in startPlayback(). The mutex ensures only one navigation runs at a time.
     */
    private val navigationMutex = Mutex()

    /** Position captured at the start of a horizontal seek gesture. */
    private var seekStartPositionMs = 0L

    /** Whether the player was playing before a horizontal seek gesture paused it. */
    private var wasPlayingBeforeHorizontalSeek = false

    /**
     * CR-010: generation counter for startPlayback().
     * Incremented at the start of each call; checked after each suspend point.
     * If the value has changed, a newer call has superseded this one — abort.
     */
    private var playbackStartToken = 0

    init {
        audioFocusManager.listener = this
        playerManager.onCodecFailure = { onCodecFailureDetected() }
        playerManager.onSourceError  = { onSourceErrorDetected() }
        playerManager.onTracksChanged = { tracks -> updateTracks(tracks) }
        playerManager.onPlaybackEnded = { onVideoEnded() }
        // Load persisted playback order
        val savedOrder = videoStateStore.loadPlaybackOrder()
        history.playbackOrder = savedOrder
        _uiState.update { it.copy(playbackOrder = savedOrder) }
        // FEAT-015: restore global brightness
        val savedBrightness = videoStateStore.loadBrightness()
        if (savedBrightness >= 0f) {
            _uiState.update { it.copy(brightness = savedBrightness) }
        }
    }

    // -------------------------------------------------------------------------
    // Intent handling
    // -------------------------------------------------------------------------

    /**
     * Entry point called by PlayerActivity when it receives an ACTION_VIEW intent.
     * Loads the playlist and starts playing the video at [uri].
     */
    fun onIntentReceived(uri: Uri) {
        intentUri = uri
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val playlist = videoRepository.listVideosInDirectory(uri)
                Log.d(TAG, "onIntentReceived: uri=$uri playlist.size=${playlist.size} " +
                    "isSwipeEnabled=${playlist.size > 1}")
                val startVideo = playlist.firstOrNull { it.uri == uri }
                    ?: videoRepository.resolveVideoFile(uri)
                    ?: playlist.first()

                // CR-005: ContentUriNoAccess only when the repository returned the
                // original URI unchanged (= buildFallbackVideoFile fallback), meaning
                // no listing strategy succeeded. A legitimate 1-video directory returns
                // a VideoFile built from the actual file, which has a different URI.
                val isSingleFileFallback = playlist.size == 1 &&
                    playlist.first().uri == uri

                history.init(startVideo, playlist)

                _uiState.update {
                    it.copy(
                        playlist = playlist,
                        currentVideo = startVideo,
                        previousVideo = null,
                        isSwipeEnabled = playlist.size > 1,
                        isLoading = false,
                        error = if (isSingleFileFallback) PlayerError.ContentUriNoAccess else null,
                    )
                }

                startPlayback(startVideo)
                // FEAT-018: if PARENT_RANDOM was already selected, load siblings now
                if (_uiState.value.playbackOrder == PlaybackOrder.PARENT_RANDOM) {
                    loadSiblingPlaylist()
                }
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
     * CRO-009: wrapped in navigationMutex to prevent PlaybackHistory corruption.
     */
    fun onSwipeUp() {
        if (!_uiState.value.isSwipeEnabled) return
        viewModelScope.launch {
            navigationMutex.withLock {
                history.current?.let { saveCurrentState(it) }
                val previous = history.current
                val next = history.navigateForward()
                switchToVideo(next, previousVideo = previous)
            }
        }
    }

    /**
     * FEAT-009 ping-pong variant: advances to the next video without touching
     * SurfaceViews. Called after the swipe-up animation completes and the
     * ping-pong has already flipped the visible surface to the preloaded player.
     * Uses [VideoPlayerManager.swapPlayersNoSurface] so ExoPlayer never detaches
     * from the already-visible SurfaceView — eliminating the post-animation flash.
     */
    fun onSwipeUpNoSurface() {
        if (!_uiState.value.isSwipeEnabled) return
        viewModelScope.launch {
            navigationMutex.withLock {
                history.current?.let { saveCurrentState(it) }
                val previous = history.current
                val next = history.navigateForward()
                switchToVideo(next, previousVideo = previous, noSurfaceSwap = true)
            }
        }
    }

    /**
     * Swipe down: go back in history.
     * No-op at the start of history (UI shows a bounce animation instead).
     * CRO-009: wrapped in navigationMutex to prevent PlaybackHistory corruption.
     * CRO-028: uses peekBack() instead of navigateBack()+navigateForward() pattern.
     */
    fun onSwipeDown() {
        if (!_uiState.value.isSwipeEnabled) return
        viewModelScope.launch {
            navigationMutex.withLock {
                // BUG-022: save current video's position before navigating away,
                // so it is restored the next time this video is navigated to.
                history.current?.let { saveCurrentState(it) }
                val prev = history.navigateBack() ?: return@withLock
                // CRO-028: peekBack() returns the video before prev without modifying index
                val beforePrev = history.peekBack()
                _uiState.update {
                    it.copy(currentVideo = prev, previousVideo = beforePrev)
                }
                startPlayback(prev)
            }
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

    fun onPlaybackOrderChange(order: PlaybackOrder) {
        history.playbackOrder = order
        _uiState.update { it.copy(playbackOrder = order) }
        videoStateStore.savePlaybackOrder(order)
        // FEAT-018: load sibling videos if switching to PARENT_RANDOM
        if (order == PlaybackOrder.PARENT_RANDOM) {
            loadSiblingPlaylist()
        }
        // Invalidate the current peek so the next preload uses the new order
        viewModelScope.launch { triggerPeekNext() }
    }

    /** FEAT-018: async scan of sibling directories for PARENT_RANDOM mode. */
    private fun loadSiblingPlaylist() {
        val uri = intentUri ?: return
        siblingLoadJob?.cancel()
        siblingLoadJob = viewModelScope.launch {
            val siblings = videoRepository.listSiblingDirectoryVideos(uri)
            history.setSiblingPlaylist(siblings)
            // Re-trigger peek with the new pool
            triggerPeekNext()
        }
    }

    fun onMirrorToggle() {
        val newMirrored = !_uiState.value.isMirrored
        _uiState.update { it.copy(isMirrored = newMirrored) }
        // Save immediately so it persists even if the user swipes without pausing
        val video = history.current ?: return
        val state = _uiState.value
        videoStateStore.save(
            filename    = video.name,
            positionMs  = state.positionMs,
            durationMs  = state.durationMs,
            zoom        = state.zoomScale,
            displayMode = state.displayMode,
            volume      = state.volume,
            isMirrored  = newMirrored,
        )
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

    fun onDisplayModeSet(mode: DisplayMode) {
        _uiState.update { it.copy(displayMode = mode) }
    }

    // Kept for tests that use the cycle behaviour.
    fun onDisplayModeChange() {
        val next = when (_uiState.value.displayMode) {
            DisplayMode.ADAPT      -> DisplayMode.FILL
            DisplayMode.FILL       -> DisplayMode.STRETCH
            DisplayMode.STRETCH    -> DisplayMode.NATIVE_100
            DisplayMode.NATIVE_100 -> DisplayMode.RATIO_1_1
            DisplayMode.RATIO_1_1  -> DisplayMode.RATIO_3_4
            DisplayMode.RATIO_3_4  -> DisplayMode.RATIO_16_9
            DisplayMode.RATIO_16_9 -> DisplayMode.ADAPT
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
        val clamped = brightness.coerceIn(0f, 1f)
        _uiState.update { it.copy(brightness = clamped) }
        videoStateStore.saveBrightness(clamped)
    }

    /** Incremental brightness update from gesture (positive = drag up = brighter). */
    fun onBrightnessDelta(delta: Float) {
        val cur = _uiState.value.brightness.let { if (it < 0f) 0.5f else it }
        val newBrightness = (cur + delta).coerceIn(0f, 1f)
        _uiState.update { it.copy(brightness = newBrightness, showBrightnessBar = true) }
        videoStateStore.saveBrightness(newBrightness)
        hideBrightnessBarJob?.cancel()
        hideBrightnessBarJob = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(showBrightnessBar = false) }
        }
    }

    fun onVolumeChange(volume: Float) {
        // FEAT-016: extended range 0–150%
        val clamped = volume.coerceIn(0f, 1.5f)
        playerManager.currentPlayer?.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
    }

    /** Incremental volume update from gesture (positive = drag up = louder). */
    fun onVolumeDelta(delta: Float) {
        val cur = _uiState.value.volume
        // FEAT-016: extended range 0–150%
        val newVolume = (cur + delta).coerceIn(0f, 1.5f)
        playerManager.currentPlayer?.volume = newVolume
        _uiState.update { it.copy(volume = newVolume, showVolumeBar = true) }
        hideVolumeBarJob?.cancel()
        hideVolumeBarJob = viewModelScope.launch {
            delay(1_500L)
            _uiState.update { it.copy(showVolumeBar = false) }
        }
    }

    // -------------------------------------------------------------------------
    // Horizontal seek (FEAT-008)
    // -------------------------------------------------------------------------

    /** Called when a horizontal swipe gesture starts on the center zone. */
    fun onHorizontalSeekStart() {
        val player = playerManager.currentPlayer ?: return
        seekStartPositionMs = player.currentPosition
        wasPlayingBeforeHorizontalSeek = _uiState.value.isPlaying
        if (wasPlayingBeforeHorizontalSeek) {
            player.pause()
            stopPositionPolling()
        }
        // Use closest keyframe for fast scrubbing previews.
        player.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        _uiState.update {
            it.copy(
                isSeekingHorizontally = true,
                horizontalSeekTargetMs = seekStartPositionMs,
                horizontalSeekDeltaMs = 0L,
            )
        }
    }

    /** Called on each drag event; [deltaX] is total displacement in pixels from gesture start.
     *  Seeks the player in real-time so the video frame updates while scrubbing. */
    fun onHorizontalSeekUpdate(deltaX: Float, screenWidthPx: Float) {
        val duration = _uiState.value.durationMs.coerceAtLeast(0L)
        val deltaMs = (deltaX / screenWidthPx * 100_000L).toLong()
        val target = (seekStartPositionMs + deltaMs).coerceIn(0L, duration)
        playerManager.currentPlayer?.seekTo(target)
        _uiState.update {
            it.copy(
                positionMs = target,
                horizontalSeekTargetMs = target,
                horizontalSeekDeltaMs = target - seekStartPositionMs,
            )
        }
    }

    /** Called when the user lifts the finger; does a final exact seek and resumes. */
    fun onHorizontalSeekEnd() {
        val target = _uiState.value.horizontalSeekTargetMs
        val player = playerManager.currentPlayer
        // Final seek with exact precision.
        player?.setSeekParameters(SeekParameters.EXACT)
        player?.seekTo(target)
        _uiState.update {
            it.copy(
                positionMs = target,
                isSeekingHorizontally = false,
                horizontalSeekDeltaMs = 0L,
            )
        }
        resumeAfterSeek(player)
    }

    /** Called when the gesture is interrupted (e.g. pinch starts); no additional seek. */
    fun onHorizontalSeekCancel() {
        val player = playerManager.currentPlayer
        player?.setSeekParameters(SeekParameters.EXACT)
        _uiState.update { it.copy(isSeekingHorizontally = false, horizontalSeekDeltaMs = 0L) }
        resumeAfterSeek(player)
    }

    // CR-017: extracted common resume logic shared by onHorizontalSeekEnd and onHorizontalSeekCancel
    private fun resumeAfterSeek(player: androidx.media3.exoplayer.ExoPlayer?) {
        if (wasPlayingBeforeHorizontalSeek) {
            player?.play()
            startPositionPolling()
            _uiState.update { it.copy(isPlaying = true) }
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

    /** CRO-014: volume before ducking; restored on unduck to preserve user preference. */
    private var preDuckVolume = 1.0f

    override fun onDuck() {
        // CRO-014: save current volume so unduck can restore it
        preDuckVolume = _uiState.value.volume
        playerManager.currentPlayer?.volume = 0.3f
    }

    override fun onUnduck() {
        // CRO-014: restore user's volume instead of hardcoding 1.0f
        playerManager.currentPlayer?.volume = preDuckVolume
        _uiState.update { it.copy(volume = preDuckVolume) }
    }

    // -------------------------------------------------------------------------
    // Lifecycle hooks (called by PlayerActivity)
    // -------------------------------------------------------------------------

    fun onActivityStart() {
        // Nothing required here; audio focus is requested at playback start
    }

    fun onActivityStop() {
        history.current?.let { saveCurrentState(it) }
        if (_uiState.value.isPlaying) {
            playerManager.currentPlayer?.pause()
            audioFocusManager.abandonFocus()
            stopPositionPolling()
            _uiState.update { it.copy(isPlaying = false) }
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

    private suspend fun startPlayback(video: VideoFile, noSurfaceSwap: Boolean = false) {
        // CR-010: generation counter — abort if a newer startPlayback() has started
        val token = ++playbackStartToken

        // Load persisted state before starting (zoom + displayMode + position + volume + mirror).
        val saved = videoStateStore.load(video.name)
        if (saved != null) {
            _uiState.update {
                it.copy(
                    zoomScale   = saved.zoom,
                    displayMode = saved.displayMode,
                    volume      = saved.volume,
                    isMirrored  = saved.isMirrored,
                )
            }
            playerManager.currentPlayer?.volume = saved.volume
        } else {
            _uiState.update {
                it.copy(zoomScale = 1f, displayMode = DisplayMode.ADAPT,
                    volume = 1f, isMirrored = false)
            }
            playerManager.currentPlayer?.volume = 1f
        }

        // BUG-022: pre-seek the preloaded next player BEFORE swapping so that
        // play() starts at the saved position instead of 0.
        // prevPlayer position is applied during triggerPeekPrev() — no seek here.
        if (saved != null && saved.positionMs > 0L &&
                playerManager.nextPlayerVideo?.uri == video.uri) {
            playerManager.nextPlayer?.seekTo(saved.positionMs)
        }

        // CR-001: use pre-loaded player if it matches the target video.
        // FEAT-009: swapPlayersNoSurface / swapPrevNoSurface avoid re-attaching
        // surfaces, eliminating post-animation flash.
        val swapped: Boolean = when {
            playerManager.nextPlayerVideo?.uri == video.uri ->
                if (noSurfaceSwap) playerManager.swapPlayersNoSurface()
                else playerManager.swapToNext()
            playerManager.prevPlayerVideo?.uri == video.uri ->
                playerManager.swapPrevNoSurface()
            else -> false
        }

        if (!swapped) {
            // CR-009: find external subtitle files to include in the media item
            val subtitleFiles = videoRepository.findExternalSubtitles(video)
            val oldPlayer = playerManager.currentPlayer
            val newPlayer = playerManager.preparePlayer(
                video,
                preloadOnly = false,
                subtitleFiles = subtitleFiles,
            )
            // CR-010: if a newer startPlayback() superseded us, release and abort
            if (token != playbackStartToken) {
                viewModelScope.launch { playerManager.releasePlayer(newPlayer, null) }
                return
            }
            oldPlayer?.let {
                if (it !== newPlayer) viewModelScope.launch { playerManager.releasePlayer(it, null) }
            }
            // Seek to saved position for fresh-prepared player
            if (saved != null && saved.positionMs > 0L) {
                newPlayer.seekTo(saved.positionMs)
            }
        }

        triggerPeekNext()
        triggerPeekPrev()
        audioFocusManager.requestFocus()
        startPositionPolling()
        _uiState.update { it.copy(isPlaying = true) }
    }

    /** Saves the current playback state for the given video. */
    private fun saveCurrentState(video: VideoFile) {
        val state = _uiState.value
        videoStateStore.save(
            filename    = video.name,
            positionMs  = state.positionMs,
            durationMs  = state.durationMs,
            zoom        = state.zoomScale,
            displayMode = state.displayMode,
            volume      = state.volume,
            isMirrored  = state.isMirrored,
        )
    }

    private suspend fun switchToVideo(
        video: VideoFile,
        previousVideo: VideoFile?,
        noSurfaceSwap: Boolean = false,
    ) {
        _uiState.update {
            it.copy(
                currentVideo = video,
                previousVideo = previousVideo,
            )
        }
        startPlayback(video, noSurfaceSwap = noSurfaceSwap)
    }

    /** Pre-selects the next video and starts buffering it in nextPlayer. */
    private fun triggerPeekNext() {
        val playlist = _uiState.value.playlist
        if (playlist.size <= 1) return
        // CRO-010: cancel any previous preload job before starting a new one
        peekJob?.cancel()
        peekJob = viewModelScope.launch {
            val next = history.peekNext()
            val subtitleFiles = videoRepository.findExternalSubtitles(next)
            playerManager.preloadNext(next, subtitleFiles)
        }
    }

    /** Pre-loads the previous video into prevPlayer for instant swipe-DOWN. */
    private fun triggerPeekPrev() {
        val playlist = _uiState.value.playlist
        if (playlist.size <= 1) return
        val prev = history.peekBack() ?: return
        peekPrevJob?.cancel()
        peekPrevJob = viewModelScope.launch {
            val subtitleFiles = videoRepository.findExternalSubtitles(prev)
            playerManager.preloadPrev(prev, subtitleFiles)
            // Apply saved position so the preloaded player is at the right frame.
            val saved = videoStateStore.load(prev.name)
            if (saved != null && saved.positionMs > 0L) {
                playerManager.prevPlayer?.seekTo(saved.positionMs)
            }
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
                // CRO-003: 250ms for smoother timecode display (was 500ms)
                delay(250L)
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
        // CR-021: message avec accents correct
        viewModelScope.launch {
            _toastEvents.emit("Codec non support\u00e9 \u2014 passage \u00e0 la vid\u00e9o suivante")
        }
        _uiState.update { it.copy(error = PlayerError.CodecNotSupported) }
        codecSkipJob?.cancel()
        codecSkipJob = viewModelScope.launch {
            delay(PlayerConfig.CODEC_FAILURE_SKIP_DELAY_MS)
            _uiState.update { it.copy(error = null) }
            if (_uiState.value.isSwipeEnabled) onSwipeUp()
        }
    }

    // CRO-009: also wrapped in navigationMutex
    private fun onSourceErrorDetected() {
        val current = history.current ?: return
        viewModelScope.launch {
            navigationMutex.withLock {
            _toastEvents.emit("Fichier introuvable : ${current.name}")

            val newPlaylist = _uiState.value.playlist.filter { it !== current }
            _uiState.update { it.copy(playlist = newPlaylist) }

            if (newPlaylist.isEmpty()) {
                _uiState.update { it.copy(error = PlayerError.FileNotFound) }
                return@launch
            }

            // Pick next video from the new (smaller) playlist
            val nextVideo = newPlaylist.firstOrNull { it !== current } ?: newPlaylist.first()

            // Re-initialise history with the clean playlist
            history.init(nextVideo, newPlaylist)
            _uiState.update {
                it.copy(
                    currentVideo = nextVideo,
                    previousVideo = null,
                    isSwipeEnabled = newPlaylist.size > 1,
                    error = null,
                )
            }
            startPlayback(nextVideo)
            } // end navigationMutex.withLock
        }
    }

    // -------------------------------------------------------------------------
    // Track detection and selection (TASK-032)
    // -------------------------------------------------------------------------

    private fun updateTracks(tracks: Tracks) {
        val audioTracks = mutableListOf<TrackInfo>()
        val subtitleTracks = mutableListOf<TrackInfo>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            when (group.type) {
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        audioTracks += TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = i,
                            label = fmt.label ?: fmt.language ?: "Audio ${audioTracks.size + 1}",
                            language = fmt.language,
                            isSelected = group.isTrackSelected(i),
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val fmt = group.getTrackFormat(i)
                        subtitleTracks += TrackInfo(
                            groupIndex = groupIndex,
                            trackIndex = i,
                            label = fmt.label ?: fmt.language ?: "Subtitle ${subtitleTracks.size + 1}",
                            language = fmt.language,
                            isSelected = group.isTrackSelected(i),
                        )
                    }
                }
            }
        }
        _uiState.update { it.copy(audioTracks = audioTracks, subtitleTracks = subtitleTracks) }
    }

    fun onAudioTrackSelected(track: TrackInfo) {
        val player = playerManager.currentPlayer ?: return
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
    }

    fun onSubtitleTrackSelected(track: TrackInfo?) {
        val player = playerManager.currentPlayer ?: return
        player.trackSelectionParameters = if (track == null) {
            // Disable all subtitles
            player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT or C.SELECTION_FLAG_FORCED)
                .build()
        } else {
            val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
            player.trackSelectionParameters.buildUpon()
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
                .build()
        }
    }

    private fun onVideoEnded() {
        if (_uiState.value.isSwipeEnabled) {
            // BUG-027: emit event so PlayerScreen triggers the TikTok animation,
            // instead of swapping players directly (which skips the visual transition).
            viewModelScope.launch { _autoSwipeUpEvent.emit(Unit) }
        } else {
            // Single file: seek to beginning and replay
            val player = playerManager.currentPlayer ?: return
            player.seekTo(0)
            player.play()
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionPollingJob?.cancel()
        codecSkipJob?.cancel()
        hideBrightnessBarJob?.cancel()
        hideVolumeBarJob?.cancel()
        clearDoubleTapJob?.cancel()
        peekJob?.cancel()
        peekPrevJob?.cancel()
        siblingLoadJob?.cancel()
        audioFocusManager.listener = null
        // CRO-005: releaseAll() nullifies all callbacks and releases players.
        // runBlocking(Dispatchers.Main.immediate) executes synchronously since
        // onCleared() is always called on the main thread — no deadlock possible.
        // close() is NOT called: VideoPlayerManager is @Singleton and its scope
        // and thread must live for the entire process lifetime.
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.Main.immediate) {
            playerManager.releaseAll()
        }
    }
}
