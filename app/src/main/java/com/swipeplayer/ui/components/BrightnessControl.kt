package com.swipeplayer.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

private val BrightnessColor = Color(0xFFFFC107)

/** CR-016: delegated to VerticalIndicatorBar. */
@Composable
fun BrightnessControl(
    brightness: Float,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    VerticalIndicatorBar(
        value = brightness,
        visible = visible,
        icon = Icons.Filled.WbSunny,
        iconTint = BrightnessColor,
        barColor = BrightnessColor,
        contentDescription = "Luminosit\u00e9",
        modifier = modifier,
    )
}
