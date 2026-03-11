package com.swipeplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 3f, 4f)

@Composable
fun SpeedSelector(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onMenuStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(onClick = {
            expanded = true
            onMenuStateChange(true)
        }) {
            Text(
                text = formatSpeed(currentSpeed),
                color = Color.White,
                fontSize = 14.sp,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                onMenuStateChange(false)
            },
        ) {
            SPEEDS.forEach { speed ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSpeed(speed),
                            fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSpeedSelected(speed)
                        expanded = false
                        onMenuStateChange(false)
                    },
                )
            }
        }
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toLong().toFloat()) "${speed.toInt()}x"
    else "${speed}x"
