package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.gesture.GestureClass

@Composable
fun GestureStateLabel(gestureClass: GestureClass?, modifier: Modifier = Modifier) {
    Text(
        text = "Gesture: ${gestureClass?.name ?: "-"}",
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
