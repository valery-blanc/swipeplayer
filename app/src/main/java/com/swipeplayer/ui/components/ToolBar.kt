package com.swipeplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
    isMirrored: Boolean = false,
    onSpeedSelected: (Float) -> Unit,
    onFormatSelected: (DisplayMode) -> Unit,
    onOrientationChange: () -> Unit,
    onMirrorToggle: () -> Unit = {},
    onMenuStateChange: (Boolean) -> Unit = {},
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
            onMenuStateChange = onMenuStateChange,
        )

        // Settings
        IconButton(onClick = onShowSettings) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Reglages",
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }

        // Format — dropdown menu with all display modes
        FormatSelector(
            currentMode = displayMode,
            onFormatSelected = onFormatSelected,
            onMenuStateChange = onMenuStateChange,
        )

        // Mirror — FEAT-017: icon flips horizontally when active
        IconButton(onClick = onMirrorToggle) {
            Icon(
                imageVector = Icons.Filled.Flip,
                contentDescription = if (isMirrored) "Miroir actif" else "Miroir inactif",
                tint = Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { scaleX = if (isMirrored) -1f else 1f },
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
