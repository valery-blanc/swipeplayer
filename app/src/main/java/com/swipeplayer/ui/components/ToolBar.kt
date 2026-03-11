package com.swipeplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swipeplayer.ui.DisplayMode
import com.swipeplayer.ui.OrientationMode
import com.swipeplayer.ui.TrackInfo

@Composable
fun ToolBar(
    currentSpeed: Float,
    displayMode: DisplayMode,
    orientationMode: OrientationMode,
    audioTracks: List<TrackInfo> = emptyList(),
    subtitleTracks: List<TrackInfo> = emptyList(),
    onSpeedSelected: (Float) -> Unit,
    onDisplayModeChange: () -> Unit,
    onOrientationChange: () -> Unit,
    onAudioTrackSelected: (TrackInfo) -> Unit = {},
    onSubtitleTrackSelected: (TrackInfo?) -> Unit = {},
    onShowSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Speed
        SpeedSelector(
            currentSpeed = currentSpeed,
            onSpeedSelected = onSpeedSelected,
        )

        // Settings — state is managed by the parent (ControlsOverlay/PlayerScreen)
        // so the auto-hide timer can be suspended while the sheet is open.
        IconButton(onClick = onShowSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Reglages",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // Display mode
        IconButton(onClick = onDisplayModeChange) {
            Icon(
                imageVector = Icons.Filled.Fullscreen,
                contentDescription = displayMode.name,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // Orientation
        IconButton(onClick = onOrientationChange) {
            Icon(
                imageVector = Icons.Filled.ScreenRotation,
                contentDescription = orientationMode.name,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
