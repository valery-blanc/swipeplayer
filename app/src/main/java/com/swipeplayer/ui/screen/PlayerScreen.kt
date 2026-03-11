package com.swipeplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.swipeplayer.ui.components.ControlsOverlay
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.ui.PlayerViewModel
import com.swipeplayer.ui.components.SettingsSheet
import com.swipeplayer.ui.components.VideoSurface
import com.swipeplayer.ui.gesture.gestureHandler
import kotlinx.coroutines.launch

/**
 * Root composable for the player screen.
 *
 * VerticalPager layout (3 logical pages, userScrollEnabled = false):
 *   page 0 = previous video (or black)
 *   page 1 = current video  <- always reset here after each swipe
 *   page 2 = next video     (or black)
 *
 * Swipes are detected by [gestureHandler] which programmatically scrolls
 * the pager. When the page actually changes, [LaunchedEffect] notifies
 * the ViewModel and silently resets the pager to page 1.
 *
 * [showSettingsSheet] is managed here (outside AnimatedVisibility) so the
 * SettingsSheet survives the controls auto-hide animation and the auto-hide
 * timer is suspended while the sheet is open (BUG-003).
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    // Settings sheet visibility lives outside AnimatedVisibility so the sheet
    // is not destroyed when the controls overlay fades out.
    var showSettingsSheet by remember { mutableStateOf(false) }

    // When the page actually changes (swipe animation completed), notify the
    // ViewModel and silently snap back to page 1.
    LaunchedEffect(pagerState.currentPage) {
        when (pagerState.currentPage) {
            0 -> {
                viewModel.onSwipeDown()
                pagerState.scrollToPage(1)
            }
            2 -> {
                viewModel.onSwipeUp()
                pagerState.scrollToPage(1)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .gestureHandler(
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx,
                zoomScale = uiState.zoomScale,
                isSwipeEnabled = uiState.isSwipeEnabled,
                canSwipeDown = uiState.previousVideo != null,
                onSwipeUp = {
                    scope.launch { pagerState.animateScrollToPage(2) }
                },
                onSwipeDown = {
                    if (uiState.previousVideo != null) {
                        scope.launch { pagerState.animateScrollToPage(0) }
                    }
                },
                onTap = viewModel::onToggleControls,
                onDoubleTapLeft = {
                    viewModel.onSeekRelative(-10_000L)
                    viewModel.onDoubleTap(com.swipeplayer.ui.TapSide.LEFT)
                },
                onDoubleTapRight = {
                    viewModel.onSeekRelative(10_000L)
                    viewModel.onDoubleTap(com.swipeplayer.ui.TapSide.RIGHT)
                },
                onZoom = viewModel::onZoomChange,
                onBrightnessDelta = viewModel::onBrightnessDelta,
                onVolumeDelta = viewModel::onVolumeDelta,
            ),
    ) {
        VerticalPager(
            state = pagerState,
            userScrollEnabled = false,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                // Only page 1 renders the active video surface.
                // Pages 0 and 2 stay black during the swipe transition;
                // the pager snap-back + ViewModel update happen after.
                if (page == 1) {
                    VideoSurface(
                        player = viewModel.currentPlayer,
                        zoomScale = uiState.zoomScale,
                        displayMode = uiState.displayMode,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

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

        // SettingsSheet is rendered OUTSIDE AnimatedVisibility so it persists
        // when the controls overlay fades out, and so the auto-hide timer can
        // be suspended while it is open.
        if (showSettingsSheet) {
            SettingsSheet(
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                onAudioTrackSelected = viewModel::onAudioTrackSelected,
                onSubtitleTrackSelected = viewModel::onSubtitleTrackSelected,
                onDismiss = { showSettingsSheet = false },
            )
        }
    }
}
