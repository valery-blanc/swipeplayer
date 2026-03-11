package com.swipeplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.util.toTimecode

/**
 * Overlay shown during a horizontal seek gesture (FEAT-008).
 * Displays the target timecode and the seek delta (e.g. "+12s" / "-5s").
 */
@Composable
fun HorizontalSeekIndicator(
    targetMs: Long,
    deltaMs: Long,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(Color(0x99000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 24.dp, vertical = 14.dp),
        ) {
            Text(
                text = targetMs.toTimecode(),
                color = Color.White,
                fontSize = 28.sp,
                fontFamily = FontFamily.Monospace,
            )
            val deltaSeconds = deltaMs / 1000
            val deltaText = if (deltaSeconds >= 0) "+${deltaSeconds}s" else "${deltaSeconds}s"
            Text(
                text = deltaText,
                color = Color(0xCCFFFFFF),
                fontSize = 16.sp,
            )
        }
    }
}
