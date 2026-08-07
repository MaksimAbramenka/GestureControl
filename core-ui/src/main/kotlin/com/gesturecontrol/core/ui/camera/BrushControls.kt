package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

data class BrushColorOption(
    val label: String,
    val r: Float,
    val g: Float,
    val b: Float,
) {
    val composeColor: Color get() = Color(r, g, b)
}

val BRUSH_COLOR_OPTIONS = listOf(
    BrushColorOption("Cyan", 0.1f, 0.9f, 1.0f),
    BrushColorOption("Red", 1.0f, 0.2f, 0.2f),
    BrushColorOption("Green", 0.2f, 1.0f, 0.3f),
    BrushColorOption("Yellow", 1.0f, 0.9f, 0.1f),
    BrushColorOption("White", 1.0f, 1.0f, 1.0f),
)

enum class BrushSizeOption(val label: String, val size: Float) {
    SMALL("S", 0.008f),
    MEDIUM("M", 0.015f),
    LARGE("L", 0.03f),
}

@Composable
fun BrushControls(
    selectedColor: BrushColorOption,
    selectedSize: BrushSizeOption,
    onSelectColor: (BrushColorOption) -> Unit,
    onSelectSize: (BrushSizeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BRUSH_COLOR_OPTIONS.forEach { option ->
                val isSelected = option == selectedColor
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(option.composeColor)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = Color.White,
                            shape = CircleShape,
                        )
                        .clickable { onSelectColor(option) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            BrushSizeOption.entries.forEach { option ->
                val isSelected = option == selectedSize
                Button(
                    onClick = { onSelectSize(option) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color.Red else Color.DarkGray,
                    ),
                ) {
                    Text(option.label)
                }
            }
        }
    }
}
