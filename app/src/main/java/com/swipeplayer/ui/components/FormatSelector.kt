package com.swipeplayer.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipeplayer.ui.DisplayMode

private val FORMAT_OPTIONS = listOf(
    DisplayMode.ADAPT      to "Adapter",
    DisplayMode.FILL       to "Remplir",
    DisplayMode.STRETCH    to "Etirer",
    DisplayMode.NATIVE_100 to "100%",
    DisplayMode.RATIO_1_1  to "1:1",
    DisplayMode.RATIO_3_4  to "3:4",
    DisplayMode.RATIO_16_9 to "16:9",
)

@Composable
fun FormatSelector(
    currentMode: DisplayMode,
    onFormatSelected: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { expanded = true },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.Fullscreen,
            contentDescription = "Format",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        FORMAT_OPTIONS.forEach { (mode, label) ->
            val isSelected = mode == currentMode
            DropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    expanded = false
                    onFormatSelected(mode)
                },
            )
        }
    }
}
