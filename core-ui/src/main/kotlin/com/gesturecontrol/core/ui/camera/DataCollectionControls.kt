package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.gesture.GestureClass

@Composable
fun DataCollectionControls(
    selectedGestureClass: GestureClass?,
    recordedRowCount: Int,
    onSelectGestureClass: (GestureClass?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Recorded: $recordedRowCount",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            GestureClass.entries.forEach { gestureClass ->
                val isSelected = gestureClass == selectedGestureClass
                Button(
                    onClick = { onSelectGestureClass(if (isSelected) null else gestureClass) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) Color.Red else Color.DarkGray,
                    ),
                ) {
                    Text(gestureClass.name)
                }
            }
        }
    }
}
