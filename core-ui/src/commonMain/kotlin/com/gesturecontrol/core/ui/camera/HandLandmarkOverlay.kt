package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.ViewportDimensions
import com.gesturecontrol.domain.hand.toViewportPoint

/** Debug HUD: draws a dot per detected hand landmark, aligned to what's actually on screen. */
@Composable
fun HandLandmarkOverlay(
    handDetectionResult: HandDetectionResult,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val viewportDimensions = ViewportDimensions(size.width, size.height)
        val dotRadius = 4.dp.toPx()

        handDetectionResult.hands.forEach { hand ->
            hand.points.forEach { point ->
                val viewportPoint =
                    point.toViewportPoint(
                        imageDimensions = handDetectionResult.imageDimensions,
                        viewportDimensions = viewportDimensions,
                        mirrored = mirrored,
                    )
                drawCircle(
                    color = Color.Green,
                    radius = dotRadius,
                    center = Offset(viewportPoint.x, viewportPoint.y),
                )
            }
        }
    }
}
