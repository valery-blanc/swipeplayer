package com.swipeplayer.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.ui.PlayerViewModel
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
 */
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

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
                onDoubleTapLeft = { viewModel.onSeekRelative(-10_000L) },
                onDoubleTapRight = { viewModel.onSeekRelative(10_000L) },
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
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Controls overlay — animated in/out.
        // Full implementation comes in TASK-029; this shows a minimal
        // status indicator in the meantime.
        AnimatedVisibility(
            visible = uiState.controlsVisible,
            enter = fadeIn(tween(PlayerConfig.CONTROLS_FADE_DURATION_MS)),
            exit = fadeOut(tween(PlayerConfig.CONTROLS_FADE_DURATION_MS)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x80000000)),
                contentAlignment = Alignment.Center,
            ) {
                val label = when {
                    uiState.isLoading -> "Chargement..."
                    uiState.error != null -> "Erreur : ${uiState.error}"
                    uiState.currentVideo != null -> uiState.currentVideo!!.name
                    else -> ""
                }
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
