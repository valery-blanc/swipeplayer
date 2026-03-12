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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.util.toTimecode

private val NetflixRed  = Color(0xFFE50914)
private val BufferColor = Color(0x80FFFFFF)
private val TrackBg     = Color(0x40FFFFFF)
private const val TRACK_HEIGHT_DP = 4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
    /** CR-015: buffered position to visualise buffer progress. */
    bufferedPositionMs: Long = 0L,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // CR-012: two distinct variables instead of the ambiguous isDragging: Float
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val fraction = if (durationMs > 0L) {
        if (isDragging) dragFraction
        else positionMs.toFloat() / durationMs.toFloat()
    } else 0f

    val displayMs = if (isDragging && durationMs > 0L) {
        (dragFraction * durationMs).toLong()
    } else positionMs

    val bufferFraction = if (durationMs > 0L)
        (bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    else 0f

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
            onValueChange = {
                isDragging = true
                dragFraction = it
            },
            onValueChangeFinished = {
                if (durationMs > 0L) {
                    onSeek((dragFraction * durationMs).toLong())
                }
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = NetflixRed,
                inactiveTrackColor = TrackBg,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
            ),
            // CR-015: custom track that draws background → buffer → played layers
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    colors = SliderDefaults.colors(
                        activeTrackColor = NetflixRed,
                        inactiveTrackColor = TrackBg,
                    ),
                    modifier = Modifier.drawBufferBehind(bufferFraction),
                )
            },
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

/** Draws a buffer progress layer behind the standard Slider track. */
private fun Modifier.drawBufferBehind(bufferFraction: Float): Modifier =
    this.drawBehind { drawBufferLayer(bufferFraction) }

private fun DrawScope.drawBufferLayer(bufferFraction: Float) {
    if (bufferFraction <= 0f) return
    val trackH = TRACK_HEIGHT_DP.dp.toPx()
    val y = (size.height - trackH) / 2f
    val radius = trackH / 2f
    drawRoundRect(
        color = BufferColor,
        topLeft = Offset(0f, y),
        size = Size(size.width * bufferFraction, trackH),
        cornerRadius = CornerRadius(radius),
    )
}
