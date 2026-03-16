package com.swipeplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.ui.PlayerViewModel
import com.swipeplayer.ui.TapSide
import com.swipeplayer.ui.components.BrightnessControl
import com.swipeplayer.ui.components.ControlsOverlay
import com.swipeplayer.ui.components.HorizontalSeekIndicator
import com.swipeplayer.ui.components.SettingsSheet
import com.swipeplayer.ui.components.VideoSurface
import com.swipeplayer.ui.components.VolumeControl
import com.swipeplayer.ui.gesture.gestureHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Root composable for the player screen.
 *
 * FEAT-009: TikTok-style swipe animation with ping-pong surfaces.
 *
 * Two VideoSurface composables (A and B) are always present. One is "front"
 * (at y=0, playing the current video) and the other is "back" (off-screen,
 * holding the preloaded next video).
 *
 * During a swipe-up gesture:
 *   - Front surface follows dragOffset (slides up).
 *   - Back surface is at screenHeightPx + dragOffset (rises from below).
 *
 * On swipe-up commit:
 *   1. [aIsFront] flips — the surface that was "back" (at y=0) becomes "front".
 *   2. [dragOffset] snaps to 0 — new front stays at y=0, new back moves to y=H.
 *   3. [viewModel.onSwipeUpNoSurface] is called — promotes nextPlayer to current
 *      WITHOUT touching SurfaceViews, eliminating the post-animation flash.
 *   4. [LaunchedEffect(nextPlayerState)] will update the new back surface when
 *      the next video is preloaded.
 *
 * On swipe-down commit:
 *   - No flip (prev video is not preloaded).
 *   - [viewModel.onSwipeDown] loads prev video async.
 *   - Wait for [currentPlayerState] to change (new player ready).
 *   - Update the front surface's player, then snapTo(0).
 *
 * [showSettingsSheet] is managed outside AnimatedVisibility so the sheet
 * survives controls fade-out and suspends the auto-hide timer (BUG-003).
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // BUG-021: use actual measured Box dimensions instead of configuration values.
    // configuration.screenHeightDp excludes system bars in fullscreen immersive mode,
    // causing the off-screen back surface to bleed into view at the bottom.
    // Initial values are overwritten by onSizeChanged on first layout frame.
    var screenWidthPx by remember { mutableStateOf(0f) }
    var screenHeightPx by remember { mutableStateOf(0f) }

    // -------------------------------------------------------------------------
    // FEAT-009: Ping-pong state
    // -------------------------------------------------------------------------

    // Which VideoSurface is currently "front" (on screen, playing current video).
    // true = A is front, false = B is front. Flips on each swipe-up commit.
    var aIsFront by remember { mutableStateOf(true) }

    // Players for each surface. Never reassigned while a surface is at y=0.
    var playerA by remember { mutableStateOf(viewModel.currentPlayer) }
    var playerB by remember { mutableStateOf(viewModel.nextPlayer) }

    // Drag offset driving both surfaces. Positive = swiping down, negative = swiping up.
    val dragOffset = remember { Animatable(0f) }

    // Direction of the current/last swipe, used to position the back surface:
    //   -1 = up  → back surface comes from below  (screenHeightPx + dragOffset)
    //    1 = down → back surface comes from above  (-screenHeightPx + dragOffset)
    //    0 = rest → back surface parked below screen
    var swipeDir by remember { mutableIntStateOf(0) }

    // When nextPlayerState changes (new preload ready), update the off-screen surface.
    val nextPlayerState by viewModel.nextPlayerState.collectAsState()
    LaunchedEffect(nextPlayerState) {
        val p = nextPlayerState ?: return@LaunchedEffect
        if (aIsFront) playerB = p   // B is back
        else          playerA = p   // A is back
    }

    // BUG-025: when the initial current player loads (or any out-of-band player change),
    // assign it to the front surface if the front is still null. This handles the race
    // where PlayerScreen first composes before preparePlayer() has set currentPlayer.
    val currentPlayerForInit by viewModel.currentPlayerState.collectAsState()
    LaunchedEffect(currentPlayerForInit) {
        val p = currentPlayerForInit ?: return@LaunchedEffect
        if (aIsFront && playerA == null) playerA = p
        else if (!aIsFront && playerB == null) playerB = p
    }

    // Settings sheet visibility lives outside AnimatedVisibility so the sheet
    // is not destroyed when the controls overlay fades out.
    var showSettingsSheet by remember { mutableStateOf(false) }

    // -------------------------------------------------------------------------
    // Surface offset computation (read in graphicsLayer draw phase — no recompose)
    // -------------------------------------------------------------------------

    // Front surface: follows drag.
    // Back surface: symmetric ping-pong for both directions.
    //   swipeDir = -1 (UP)  → back rises from below: screenHeightPx + dragOffset
    //   swipeDir =  1 (DOWN) → back drops from above: -screenHeightPx + dragOffset
    //   swipeDir =  0 (rest) → parked below: screenHeightPx
    // If back player not yet assigned, surface stays parked (avoids black rectangle).
    fun frontOffset(): Float = dragOffset.value
    fun backOffset(): Float {
        if (swipeDir == 0) return screenHeightPx
        val backHasPlayer = if (aIsFront) playerB != null else playerA != null
        if (!backHasPlayer) return if (swipeDir == -1) screenHeightPx else -screenHeightPx
        return when (swipeDir) {
            -1 -> screenHeightPx + dragOffset.value
             1 -> -screenHeightPx + dragOffset.value
            else -> screenHeightPx
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                screenWidthPx = size.width.toFloat()
                screenHeightPx = size.height.toFloat()
            }
            .gestureHandler(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                zoomScale = { uiState.zoomScale },
                isSwipeEnabled = uiState.isSwipeEnabled,
                canSwipeDown = { uiState.previousVideo != null },
                // ------------------------------------------------------------------
                // Swipe UP: ping-pong flip — no surface re-attachment, no flash.
                // ------------------------------------------------------------------
                onSwipeUp = {
                    swipeDir = -1
                    scope.launch {
                        // BUG-024: if the back player isn't set yet, wait up to 2s for
                        // the preload to finish before animating — avoids black square.
                        val backIsReady = if (aIsFront) playerB != null else playerA != null
                        if (!backIsReady) {
                            val loaded = kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                                viewModel.nextPlayerState.first { it != null }
                            }
                            if (loaded != null) {
                                if (aIsFront) playerB = loaded else playerA = loaded
                            }
                        }
                        dragOffset.animateTo(
                            -screenHeightPx,
                            tween(PlayerConfig.SWIPE_TRANSITION_MS),
                        )
                        // At this point:
                        //   front surface: y = -H  (off screen top)
                        //   back  surface: y = H + (-H) = 0  (visible, showing nextPlayer)

                        // Flip: the surface at y=0 (back) becomes the new front.
                        val wasAFront = aIsFront
                        aIsFront = !aIsFront
                        dragOffset.snapTo(0f)
                        // New front: y=0 (unchanged) ✓
                        // New back: y=H (off screen below) ✓
                        swipeDir = 0

                        // Tell ViewModel: advance without touching SurfaceViews.
                        // swapPlayersNoSurface() starts playing the preloaded player
                        // that is ALREADY on the now-visible SurfaceView.
                        viewModel.onSwipeUpNoSurface()

                        // Clear the old player reference from the new back surface.
                        // LaunchedEffect(nextPlayerState) will set the new preload.
                        if (wasAFront) playerA = null   // A is now back
                        else           playerB = null   // B is now back
                    }
                },
                // ------------------------------------------------------------------
                // Swipe DOWN: symmetric ping-pong flip (mirror of swipe UP).
                // onVideoDragUpdate assigned prevPlayer to back surface on first drag.
                // ------------------------------------------------------------------
                onSwipeDown = {
                    // swipeDir = 1 already set by onVideoDragUpdate first call.
                    scope.launch {
                        // If prevPlayer wasn't preloaded yet, wait up to 2s for it.
                        val backIsReady = if (aIsFront) playerB != null else playerA != null
                        if (!backIsReady) {
                            val loaded = kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                                viewModel.prevPlayerState.first { it != null }
                            }
                            if (loaded != null) {
                                if (aIsFront) playerB = loaded else playerA = loaded
                            }
                        }

                        dragOffset.animateTo(screenHeightPx, tween(PlayerConfig.SWIPE_TRANSITION_MS))
                        // front: y=H (off screen bottom)
                        // back:  y = -H + H = 0 (on screen, showing prevPlayer)

                        val wasAFront = aIsFront
                        aIsFront = !aIsFront
                        dragOffset.snapTo(0f)
                        swipeDir = 0

                        viewModel.onSwipeDown()

                        // Clear old player from new back; LaunchedEffect(nextPlayerState)
                        // will fill it when the new preload completes.
                        if (wasAFront) playerA = null
                        else           playerB = null
                    }
                },
                // ------------------------------------------------------------------
                // Real-time drag: update dragOffset and set swipe direction.
                // ------------------------------------------------------------------
                onVideoDragUpdate = { dy ->
                    if (dragOffset.value == 0f && swipeDir == 0) {
                        swipeDir = if (dy < 0) -1 else if (dy > 0) 1 else 0
                        // For DOWN drag: load prevPlayer into back surface now so it is
                        // visible rising from above during the drag (symmetric with UP).
                        if (swipeDir == 1) {
                            val prevP = viewModel.prevPlayer
                            if (aIsFront) playerB = prevP else playerA = prevP
                        }
                    }
                    scope.launch { dragOffset.snapTo(dy) }
                },
                // ------------------------------------------------------------------
                // Drag cancelled: spring back to rest position.
                // ------------------------------------------------------------------
                onVideoDragCancel = {
                    scope.launch {
                        dragOffset.animateTo(
                            0f,
                            spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        )
                        swipeDir = 0
                        // If a DOWN drag swapped the back surface to prevPlayer, restore
                        // it to nextPlayer so the next swipe UP works correctly.
                        val nextP = viewModel.nextPlayer
                        if (aIsFront) { if (playerB !== nextP) playerB = nextP }
                        else          { if (playerA !== nextP) playerA = nextP }
                    }
                },
                onTap = viewModel::onToggleControls,
                onDoubleTapLeft = {
                    viewModel.onSeekRelative(-10_000L)
                    viewModel.onDoubleTap(TapSide.LEFT)
                },
                onDoubleTapRight = {
                    viewModel.onSeekRelative(10_000L)
                    viewModel.onDoubleTap(TapSide.RIGHT)
                },
                onZoom = viewModel::onZoomChange,
                onBrightnessDelta = viewModel::onBrightnessDelta,
                onVolumeDelta = viewModel::onVolumeDelta,
                onHorizontalSeekStart = viewModel::onHorizontalSeekStart,
                onHorizontalSeekUpdate = viewModel::onHorizontalSeekUpdate,
                onHorizontalSeekEnd = viewModel::onHorizontalSeekEnd,
                onHorizontalSeekCancel = viewModel::onHorizontalSeekCancel,
            ),
    ) {
        // -------------------------------------------------------------------------
        // Surface B — back when aIsFront, front when !aIsFront
        // -------------------------------------------------------------------------
        VideoSurface(
            player = playerB,
            zoomScale = if (!aIsFront) { { uiState.zoomScale } } else { { 1f } },
            displayMode = uiState.displayMode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (!aIsFront) frontOffset() else backOffset()
                },
        )

        // -------------------------------------------------------------------------
        // Surface A — front when aIsFront, back when !aIsFront
        // -------------------------------------------------------------------------
        VideoSurface(
            player = playerA,
            zoomScale = if (aIsFront) { { uiState.zoomScale } } else { { 1f } },
            displayMode = uiState.displayMode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = if (aIsFront) frontOffset() else backOffset()
                },
        )

        // Controls overlay — animated in/out (200ms fade per spec)
        AnimatedVisibility(
            visible = uiState.controlsVisible,
            enter = fadeIn(tween(PlayerConfig.CONTROLS_FADE_DURATION_MS)),
            exit = fadeOut(tween(PlayerConfig.CONTROLS_FADE_DURATION_MS)),
        ) {
            ControlsOverlay(
                uiState = uiState,
                viewModel = viewModel,
                onBack = onBack,
                showSettingsSheet = showSettingsSheet,
                onShowSettings = { showSettingsSheet = true },
            )
        }

        // Brightness bar — outside AnimatedVisibility so it is visible even when
        // the main controls are hidden (BUG-011).
        BrightnessControl(
            brightness = uiState.brightness.coerceAtLeast(0f),
            visible = uiState.showBrightnessBar,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .wrapContentWidth(),
        )

        // Volume bar — same rationale as BrightnessControl (BUG-011).
        VolumeControl(
            volume = uiState.volume,
            visible = uiState.showVolumeBar,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .wrapContentWidth(),
        )

        // Horizontal seek indicator — shown during swipe-seek gesture (FEAT-008).
        if (uiState.isSeekingHorizontally) {
            HorizontalSeekIndicator(
                targetMs = uiState.horizontalSeekTargetMs,
                deltaMs = uiState.horizontalSeekDeltaMs,
            )
        }

        // SettingsSheet is rendered OUTSIDE AnimatedVisibility so it persists
        // when the controls overlay fades out, and so the auto-hide timer can
        // be suspended while it is open.
        if (showSettingsSheet) {
            SettingsSheet(
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                playbackOrder = uiState.playbackOrder,
                onAudioTrackSelected = viewModel::onAudioTrackSelected,
                onSubtitleTrackSelected = viewModel::onSubtitleTrackSelected,
                onPlaybackOrderChange = viewModel::onPlaybackOrderChange,
                onDismiss = { showSettingsSheet = false },
            )
        }
    }
}

