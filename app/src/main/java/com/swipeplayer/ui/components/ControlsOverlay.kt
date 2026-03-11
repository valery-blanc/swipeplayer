package com.swipeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.swipeplayer.player.PlayerConfig
import com.swipeplayer.ui.PlayerUiState
import com.swipeplayer.ui.PlayerViewModel
import kotlinx.coroutines.delay

/**
 * Full-screen controls overlay assembled from all UI components.
 *
 * Layout:
 *   TopBar               — top
 *   CenterControls       — vertically centered
 *   ProgressBar+ToolBar  — bottom
 *   BrightnessControl    — center-left (visible only during gesture)
 *   VolumeControl        — center-right (visible only during gesture)
 *   DoubleTapFeedback    — full-screen (visible only after double-tap)
 *
 * Auto-hide: when controls become visible a 4-second timer starts.
 * The timer is suspended while [showSettingsSheet] is true so the settings
 * bottom sheet is not closed by the auto-hide mechanism.
 */
@Composable
fun ControlsOverlay(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    showSettingsSheet: Boolean,
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Track whether any dropdown menu (speed, format) is currently open.
    var isAnyMenuOpen by remember { mutableStateOf(false) }

    // Auto-hide timer: restart whenever controls become visible.
    // Suspended while the settings sheet or any dropdown menu is open.
    LaunchedEffect(uiState.controlsVisible, showSettingsSheet, isAnyMenuOpen) {
        if (uiState.controlsVisible && !showSettingsSheet && !isAnyMenuOpen) {
            delay(PlayerConfig.CONTROLS_HIDE_DELAY_MS)
            viewModel.onToggleControls()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0x80000000)),
    ) {
        // Top bar
        TopBar(
            title = uiState.currentVideo?.name ?: "",
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )

        // Center play controls
        CenterControls(
            isPlaying = uiState.isPlaying,
            onPlayPause = viewModel::onPlayPause,
            onSeekBack = { viewModel.onSeekRelative(-10_000L) },
            onSeekForward = { viewModel.onSeekRelative(10_000L) },
            modifier = Modifier.align(Alignment.Center),
        )

        // Bottom: progress bar + toolbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
        ) {
            ProgressBar(
                positionMs = uiState.positionMs,
                durationMs = uiState.durationMs,
                onSeek = viewModel::onSeek,
            )
            ToolBar(
                currentSpeed = uiState.playbackSpeed,
                displayMode = uiState.displayMode,
                orientationMode = uiState.orientationMode,
                audioTracks = uiState.audioTracks,
                subtitleTracks = uiState.subtitleTracks,
                onSpeedSelected = viewModel::onSpeedChange,
                onFormatSelected = viewModel::onDisplayModeSet,
                onOrientationChange = viewModel::onOrientationChange,
                onMenuStateChange = { isAnyMenuOpen = it },
                onAudioTrackSelected = viewModel::onAudioTrackSelected,
                onSubtitleTrackSelected = viewModel::onSubtitleTrackSelected,
                onShowSettings = onShowSettings,
            )
        }

        // Brightness bar (left side, visible during gesture)
        BrightnessControl(
            brightness = uiState.brightness.coerceAtLeast(0f),
            visible = uiState.showBrightnessBar,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .wrapContentWidth(),
        )

        // Volume bar (right side, visible during gesture)
        VolumeControl(
            volume = uiState.volume,
            visible = uiState.showVolumeBar,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .wrapContentWidth(),
        )

        // Double-tap seek feedback
        DoubleTapFeedback(
            side = uiState.doubleTapSide,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
