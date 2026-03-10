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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.swipeplayer.ui.DisplayMode
import com.swipeplayer.ui.OrientationMode

@Composable
fun ToolBar(
    currentSpeed: Float,
    displayMode: DisplayMode,
    orientationMode: OrientationMode,
    onSpeedSelected: (Float) -> Unit,
    onDisplayModeChange: () -> Unit,
    onOrientationChange: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }

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

        // Settings
        IconButton(onClick = { showSettings = true }) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Réglages",
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

    if (showSettings) {
        SettingsSheet(onDismiss = { showSettings = false })
    }
}
