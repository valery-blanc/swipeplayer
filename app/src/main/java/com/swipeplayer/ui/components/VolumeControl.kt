package com.swipeplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val VolumeColor = Color(0xFF2196F3)

/** CR-016: delegated to VerticalIndicatorBar.
 *  FEAT-016: volume range is 0–1.5f; normalised to 0–1 for bar display. */
@Composable
fun VolumeControl(
    volume: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    VerticalIndicatorBar(
        value = (volume / 1.5f).coerceIn(0f, 1f),
        visible = visible,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        iconTint = VolumeColor,
        barColor = VolumeColor,
        contentDescription = "Volume",
        modifier = modifier,
    )
}
