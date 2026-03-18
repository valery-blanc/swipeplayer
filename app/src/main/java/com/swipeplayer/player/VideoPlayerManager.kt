package com.swipeplayer.player

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.swipeplayer.data.SubtitleFile
import com.swipeplayer.data.VideoFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of up to 2 ExoPlayer instances:
 *   currentPlayer  - the video currently on screen and playing
 *   nextPlayer     - the next video, buffered and paused (preloaded by peekNext)
 *
 * All ExoPlayer API calls happen on the Main dispatcher (ExoPlayer requires its
 * looper thread). Heavy I/O (final release) also runs on Main since release()
 * must be called on the thread that created the player.
 *
 * Usage pattern:
 *   1. preparePlayer(video, preloadOnly=false) -> current player starts playing
 *   2. preloadNext(nextVideo)                 -> next player buffers in background
 *   3. swapToNext()                           -> on swipe: next becomes current
 *   4. releaseAll()                           -> on ViewModel.onCleared()
 *
 * This is a @Singleton and lives for the entire process lifetime.
 * Do NOT call close() — the scope and thread must remain alive.
 */
@Singleton
class VideoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    var currentPlayer: ExoPlayer? = null
        private set

    var nextPlayer: ExoPlayer? = null
        private set

    /** Video currently loaded in [nextPlayer]; null if no preload is active. */
    var nextPlayerVideo: VideoFile? = null
        private set

    var prevPlayer: ExoPlayer? = null
        private set

    /** Video currently loaded in [prevPlayer]; null if no preload is active. */
    var prevPlayerVideo: VideoFile? = null
        private set

    /**
     * Weak reference to the SurfaceView currently attached to [currentPlayer].
     * WeakReference prevents Activity leaks since this singleton outlives Activities.
     * Stored by [attachSurface] so [swapToNext] can detach/reattach without
     * needing the caller to pass it again.
     * CRO-007: using WeakReference<SurfaceView> to avoid Activity leak via Singleton.
     */
    private var currentSurfaceRef: WeakReference<SurfaceView>? = null
    val currentSurfaceView: SurfaceView? get() = currentSurfaceRef?.get()

    /** Called when the current player's hardware decoder fails to initialise. */
    var onCodecFailure: (() -> Unit)? = null

    /** Called when the source file cannot be read (e.g. deleted between listing and play). */
    var onSourceError: (() -> Unit)? = null

    /** Called when the current player's track groups change (STATE_READY). */
    var onTracksChanged: ((Tracks) -> Unit)? = null

    /** Called when the current player reaches STATE_ENDED (video finished). */
    var onPlaybackEnded: (() -> Unit)? = null

    /** StateFlow of the current (playing) ExoPlayer; null before first video loads. */
    private val _currentPlayerState = MutableStateFlow<ExoPlayer?>(null)
    val currentPlayerState: StateFlow<ExoPlayer?> = _currentPlayerState.asStateFlow()

    /**
     * StateFlow of the preloaded next ExoPlayer; null when no preload is active.
     * Observed by PlayerScreen ping-pong to update the off-screen surface layer
     * once a new video is buffered (FEAT-009).
     */
    private val _nextPlayerState = MutableStateFlow<ExoPlayer?>(null)
    val nextPlayerState: StateFlow<ExoPlayer?> = _nextPlayerState.asStateFlow()

    /**
     * StateFlow of the preloaded previous ExoPlayer; null when no preload is active.
     * Symmetrical to [nextPlayerState] — enables instant swipe-DOWN transitions.
     */
    private val _prevPlayerState = MutableStateFlow<ExoPlayer?>(null)
    val prevPlayerState: StateFlow<ExoPlayer?> = _prevPlayerState.asStateFlow()

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // CRO-018: dispatch ExoPlayer callbacks to the main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    // CRO-013: lazy start — thread only starts when first player is created
    // Shared background thread for all ExoPlayer instances.
    // ExoPlayer requires that all players sharing the same LoadControl also share
    // the same playback looper — this thread provides that shared looper.
    private val playbackThread by lazy {
        HandlerThread("SwipePlayer-Playback").also { it.start() }
    }

    // -------------------------------------------------------------------------
    // Player creation
    // -------------------------------------------------------------------------

    @OptIn(UnstableApi::class)
    private fun createExoPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setRenderersFactory(PlayerConfig.renderersFactory(context))
            .setLoadControl(PlayerConfig.loadControl)
            .setPlaybackLooper(playbackThread.looper)  // shared looper required when sharing LoadControl
            .build()

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates, prepares, and returns an [ExoPlayer] loaded with [video].
     *
     * If [preloadOnly] is true the player is prepared (first frame decoded) but
     * NOT set to play — it stays paused, ready for an instant swap.
     * If false the player starts playing immediately.
     *
     * [subtitleFiles] are external subtitle files (.srt/.ass/.ssa) to include
     * in the media item so ExoPlayer can render them alongside the video.
     *
     * Must be called from a coroutine; switches to Main dispatcher internally.
     */
    suspend fun preparePlayer(
        video: VideoFile,
        preloadOnly: Boolean = false,
        subtitleFiles: List<SubtitleFile> = emptyList(),
    ): ExoPlayer =
        withContext(Dispatchers.Main) {
            val player = createExoPlayer()
            player.addListener(buildListener(player))
            val subtitleConfigs = subtitleFiles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(sub.uri)
                    .setMimeType(sub.mimeType)
                    .apply { if (sub.language != null) setLanguage(sub.language) }
                    .build()
            }
            val mediaItem = MediaItem.Builder()
                .setUri(video.uri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.name).build())
                .setSubtitleConfigurations(subtitleConfigs)
                .build()
            player.setMediaItem(mediaItem)
            player.playWhenReady = !preloadOnly
            player.prepare()
            if (!preloadOnly) {
                currentPlayer = player
                _currentPlayerState.value = player
            }
            player
        }

    /**
     * Buffers [video] into [nextPlayer] so it is ready before the user swipes.
     * Releases any previously pre-loaded player first.
     *
     * [subtitleFiles] are forwarded to [preparePlayer] so the pre-loaded player
     * already has subtitle tracks configured when it becomes current.
     */
    suspend fun preloadNext(video: VideoFile, subtitleFiles: List<SubtitleFile> = emptyList()) {
        val old = nextPlayer
        nextPlayer = null
        nextPlayerVideo = null
        old?.let { releasePlayer(it, surfaceView = null) }
        nextPlayer = preparePlayer(video, preloadOnly = true, subtitleFiles = subtitleFiles)
        nextPlayerVideo = video
        _nextPlayerState.value = nextPlayer
    }

    /**
     * Swaps [nextPlayer] into [currentPlayer] using the stored [currentSurfaceView]:
     *   1. Detaches the surface from the old player (prevents double-render).
     *   2. Attaches the surface to the next player and starts playback.
     *   3. Replaces currentPlayer reference and updates [currentPlayerState].
     *   4. Releases the old current player asynchronously.
     *
     * Returns true if the swap was performed, false if [nextPlayer] or
     * [currentSurfaceView] is null (WeakReference was GC'd).
     */
    fun swapToNext(): Boolean {
        val sv = currentSurfaceView ?: return false
        val newCurrent = nextPlayer ?: return false
        val oldCurrent = currentPlayer

        oldCurrent?.clearVideoSurfaceView(sv)

        newCurrent.setVideoSurfaceView(sv)
        newCurrent.play()

        currentPlayer = newCurrent
        _currentPlayerState.value = newCurrent
        nextPlayer = null
        nextPlayerVideo = null
        _nextPlayerState.value = null

        if (oldCurrent != null) {
            managerScope.launch {
                releasePlayer(oldCurrent, surfaceView = null)
            }
        }
        return true
    }

    /**
     * FEAT-009 ping-pong: promotes [nextPlayer] to [currentPlayer] and starts
     * playback WITHOUT touching any SurfaceView.
     *
     * Used when PlayerScreen's ping-pong already has the next video visible on
     * the correct SurfaceView — re-attaching surfaces would cause a visible flash.
     * The caller (PlayerScreen) is responsible for managing surface associations.
     *
     * Returns true if the swap succeeded, false if [nextPlayer] is null.
     */
    fun swapPlayersNoSurface(): Boolean {
        val newCurrent = nextPlayer ?: return false
        val oldCurrent = currentPlayer

        newCurrent.play()
        currentPlayer = newCurrent
        _currentPlayerState.value = newCurrent
        nextPlayer = null
        nextPlayerVideo = null
        _nextPlayerState.value = null

        if (oldCurrent != null) {
            // Release without surface clearing — its SurfaceView is off-screen at this point.
            managerScope.launch { releasePlayer(oldCurrent, surfaceView = null) }
        }
        return true
    }

    /**
     * Buffers [video] into [prevPlayer] so swipe-DOWN is instant (symmetric to
     * [preloadNext] for swipe-UP). Releases any previously pre-loaded prev player.
     */
    suspend fun preloadPrev(video: VideoFile, subtitleFiles: List<SubtitleFile> = emptyList()) {
        val old = prevPlayer
        prevPlayer = null
        prevPlayerVideo = null
        old?.let { releasePlayer(it, surfaceView = null) }
        prevPlayer = preparePlayer(video, preloadOnly = true, subtitleFiles = subtitleFiles)
        prevPlayerVideo = video
        _prevPlayerState.value = prevPlayer
    }

    /**
     * Promotes [prevPlayer] to [currentPlayer] without touching any SurfaceView.
     * Symmetric to [swapPlayersNoSurface] for the swipe-DOWN direction.
     * Returns true if the swap succeeded, false if [prevPlayer] is null.
     */
    fun swapPrevNoSurface(): Boolean {
        val newCurrent = prevPlayer ?: return false
        val oldCurrent = currentPlayer

        newCurrent.play()
        currentPlayer = newCurrent
        _currentPlayerState.value = newCurrent
        prevPlayer = null
        prevPlayerVideo = null
        _prevPlayerState.value = null

        if (oldCurrent != null) {
            managerScope.launch { releasePlayer(oldCurrent, surfaceView = null) }
        }
        return true
    }

    /**
     * Attaches [surfaceView] to [player] for video rendering.
     * Stores a WeakReference to the surface so [swapToNext] can detach/reattach
     * without needing the caller to pass it again.
     * Must be called on the main thread.
     */
    fun attachSurface(player: ExoPlayer, surfaceView: SurfaceView) {
        // CRO-007: WeakReference prevents retaining the Activity via its SurfaceView
        currentSurfaceRef = WeakReference(surfaceView)
        player.setVideoSurfaceView(surfaceView)
    }

    /**
     * Detaches [surfaceView] from [player].
     * Must be called before releasing the player to prevent GL surface leaks.
     */
    fun detachSurface(player: ExoPlayer, surfaceView: SurfaceView) {
        player.clearVideoSurfaceView(surfaceView)
    }

    /**
     * Releases [player] in the correct order to prevent resource leaks:
     *   1. clearVideoSurfaceView (if surfaceView provided)
     *   2. stop()
     *   3. clearMediaItems()
     *   4. release()
     *
     * All steps run on the Main dispatcher (ExoPlayer's required thread).
     */
    suspend fun releasePlayer(player: ExoPlayer, surfaceView: SurfaceView?) =
        withContext(Dispatchers.Main) {
            surfaceView?.let { player.clearVideoSurfaceView(it) }
            player.stop()
            player.clearMediaItems()
            player.release()
        }

    /**
     * Releases both [currentPlayer] and [nextPlayer] and clears all callbacks.
     * Safe to call even if one or both are null.
     * Called from ViewModel.onCleared() — the Singleton scope and thread remain alive.
     *
     * CRO-005: close() has been removed. This Singleton must not cancel its scope
     * or stop its thread — they must live for the entire process lifetime.
     */
    suspend fun releaseAll() {
        val c = currentPlayer
        val n = nextPlayer
        val p = prevPlayer
        currentPlayer = null
        nextPlayer = null
        nextPlayerVideo = null
        prevPlayer = null
        prevPlayerVideo = null
        currentSurfaceRef = null
        _currentPlayerState.value = null
        _nextPlayerState.value = null
        _prevPlayerState.value = null
        // CRO-012: null callbacks so the Singleton does not retain the ViewModel
        onCodecFailure = null
        onSourceError = null
        onTracksChanged = null
        onPlaybackEnded = null

        c?.let { releasePlayer(it, surfaceView = null) }
        n?.let { releasePlayer(it, surfaceView = null) }
        p?.let { releasePlayer(it, surfaceView = null) }
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    /**
     * CRO-018: All listener callbacks are dispatched to the main thread via
     * [mainHandler]. ExoPlayer invokes listeners on [playbackThread] (the looper
     * passed to setPlaybackLooper), not the main thread. Dispatching to main
     * ensures all callback handlers run on the correct thread.
     */
    private fun buildListener(player: ExoPlayer): Player.Listener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                mainHandler.post {
                    if (player !== currentPlayer) return@post
                    when {
                        isCodecInitFailure(error) -> onCodecFailure?.invoke()
                        isSourceError(error)      -> onSourceError?.invoke()
                    }
                }
            }
            override fun onTracksChanged(tracks: Tracks) {
                mainHandler.post {
                    if (player === currentPlayer) onTracksChanged?.invoke(tracks)
                }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                mainHandler.post {
                    if (playbackState == Player.STATE_ENDED && player === currentPlayer) {
                        onPlaybackEnded?.invoke()
                    }
                }
            }
        }

    private fun isCodecInitFailure(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.cause?.javaClass?.name?.contains("DecoderInitializationException") == true

    private fun isSourceError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
}
