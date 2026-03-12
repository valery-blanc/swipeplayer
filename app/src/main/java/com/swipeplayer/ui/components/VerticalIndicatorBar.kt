package com.swipeplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

private val BarBackground = Color(0x40FFFFFF)

/**
 * CR-016: shared vertical indicator bar used by BrightnessControl and VolumeControl.
 *
 * Displays an icon above a vertical filled bar showing [value] (0..1).
 * Animates in/out with a 100ms fade.
 */
@Composable
fun VerticalIndicatorBar(
    value: Float,
    visible: Boolean,
    icon: ImageVector,
    iconTint: Color,
    barColor: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(100)),
        exit = fadeOut(tween(100)),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.width(24.dp),
            )
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(BarBackground),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .fillMaxHeight(value.coerceIn(0f, 1f))
                        .background(barColor),
                )
            }
        }
    }
}
