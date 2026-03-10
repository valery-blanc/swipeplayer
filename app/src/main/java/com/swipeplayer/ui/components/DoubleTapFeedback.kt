package com.swipeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.ui.TapSide

/**
 * Animated feedback overlay shown after a double-tap seek.
 * Fade-in 100 ms, fade-out 400 ms (total ~500 ms, cleared by ViewModel).
 */
@Composable
fun DoubleTapFeedback(
    side: TapSide?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = side == TapSide.LEFT,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(120.dp)
                .fillMaxHeight(),
        ) {
            SeekFeedbackContent(label = "-10s", icon = {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            })
        }

        AnimatedVisibility(
            visible = side == TapSide.RIGHT,
            enter = fadeIn(tween(100)),
            exit = fadeOut(tween(400)),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(120.dp)
                .fillMaxHeight(),
        ) {
            SeekFeedbackContent(label = "+10s", icon = {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            })
        }
    }
}

@Composable
private fun SeekFeedbackContent(
    label: String,
    icon: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        icon()
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
        )
    }
}
