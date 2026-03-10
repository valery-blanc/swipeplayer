package com.swipeplayer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CenterControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Rewind 10 s
        IconButton(onClick = onSeekBack, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = "-10s",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }

        // Play / Pause
        IconButton(onClick = onPlayPause, modifier = Modifier.size(80.dp)) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Lecture",
                tint = Color.White,
                modifier = Modifier.size(64.dp),
            )
        }

        // Forward 10 s
        IconButton(onClick = onSeekForward, modifier = Modifier.size(64.dp)) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = "+10s",
                tint = Color.White,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}
