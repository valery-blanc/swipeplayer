package com.swipeplayer.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.util.toTimecode

private val NetflixRed = Color(0xFFE50914)
private val TrackBg    = Color(0x40FFFFFF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // True during a drag; lets us display the drag position without waiting
    // for the ViewModel to update positionMs.
    var isDragging by remember { mutableFloatStateOf(-1f) }

    val fraction = if (durationMs > 0L) {
        if (isDragging >= 0f) isDragging
        else positionMs.toFloat() / durationMs.toFloat()
    } else 0f

    val displayMs = if (isDragging >= 0f && durationMs > 0L) {
        (isDragging * durationMs).toLong()
    } else positionMs

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Elapsed time
        Text(
            text = displayMs.toTimecode(),
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )

        Slider(
            value = fraction,
            onValueChange = { isDragging = it },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    onSeek((isDragging * durationMs).toLong())
                }
                isDragging = -1f
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = TrackBg,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            interactionSource = remember { MutableInteractionSource() },
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
        )

        // Total duration
        Text(
            text = durationMs.coerceAtLeast(0L).toTimecode(),
            color = Color.White,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}
