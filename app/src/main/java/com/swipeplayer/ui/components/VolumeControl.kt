package com.swipeplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val VolumeColor = Color(0xFF2196F3)

/** CR-016: delegated to VerticalIndicatorBar.
 *  FEAT-020: volume range is 0–2.0f; 50% bar = 100% system volume. */
@Composable
fun VolumeControl(
    volume: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    VerticalIndicatorBar(
        value = (volume / 2.0f).coerceIn(0f, 1f),
        visible = visible,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        iconTint = VolumeColor,
        barColor = VolumeColor,
        contentDescription = "Volume",
        modifier = modifier,
    )
}
