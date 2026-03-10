package com.swipeplayer.player

import android.content.Context
import android.view.SurfaceView
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.swipeplayer.data.VideoFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 *   3. swapToNext(surfaceView)                -> on swipe: next becomes current
 *   4. releaseAll()                           -> on destroy
 */
@Singleton
class VideoPlayerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    var currentPlayer: ExoPlayer? = null
        private set

    var nextPlayer: ExoPlayer? = null
        private set

    /** Called when the current player's hardware decoder fails to initialise. */
    var onCodecFailure: (() -> Unit)? = null

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // -------------------------------------------------------------------------
    // Player creation
    // -------------------------------------------------------------------------

    @OptIn(UnstableApi::class)
    private fun createExoPlayer(): ExoPlayer =
        ExoPlayer.Builder(context)
            .setRenderersFactory(PlayerConfig.renderersFactory(context))
            .setLoadControl(PlayerConfig.loadControl)
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
     * Must be called from a coroutine; switches to Main dispatcher internally.
     */
    suspend fun preparePlayer(video: VideoFile, preloadOnly: Boolean = false): ExoPlayer =
        withContext(Dispatchers.Main) {
            val player = createExoPlayer()
            player.addListener(buildListener(player))
            player.setMediaItem(MediaItem.fromUri(video.uri))
            player.playWhenReady = !preloadOnly
            player.prepare()
            player
        }

    /**
     * Buffers [video] into [nextPlayer] so it is ready before the user swipes.
     * Releases any previously pre-loaded player first.
     */
    suspend fun preloadNext(video: VideoFile) {
        val old = nextPlayer
        nextPlayer = null
        old?.let { releasePlayer(it, surfaceView = null) }
        nextPlayer = preparePlayer(video, preloadOnly = true)
    }

    /**
     * Swaps [nextPlayer] into [currentPlayer]:
     *   1. Attaches [surfaceView] to the next player and starts playback.
     *   2. Replaces currentPlayer reference.
     *   3. Releases the old current player asynchronously (no surface leak).
     *
     * No-op if [nextPlayer] is null.
     */
    fun swapToNext(surfaceView: SurfaceView) {
        val newCurrent = nextPlayer ?: return
        val oldCurrent = currentPlayer

        attachSurface(newCurrent, surfaceView)
        newCurrent.play()

        currentPlayer = newCurrent
        nextPlayer = null

        if (oldCurrent != null) {
            managerScope.launch {
                releasePlayer(oldCurrent, surfaceView = null)
            }
        }
    }

    /**
     * Attaches [surfaceView] to [player] for video rendering.
     * Must be called on the main thread.
     */
    fun attachSurface(player: ExoPlayer, surfaceView: SurfaceView) {
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
     * Releases both [currentPlayer] and [nextPlayer].
     * Safe to call even if one or both are null.
     * Also cancels the internal manager scope.
     */
    suspend fun releaseAll() {
        val c = currentPlayer
        val n = nextPlayer
        currentPlayer = null
        nextPlayer = null

        c?.let { releasePlayer(it, surfaceView = null) }
        n?.let { releasePlayer(it, surfaceView = null) }
    }

    /** Cancels internal scope; call from ViewModel.onCleared if needed. */
    fun close() {
        managerScope.cancel()
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    private fun buildListener(player: ExoPlayer): Player.Listener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isCodecInitFailure(error)) {
                    onCodecFailure?.invoke()
                }
            }
        }

    private fun isCodecInitFailure(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            error.cause?.javaClass?.name?.contains("DecoderInitializationException") == true
}
