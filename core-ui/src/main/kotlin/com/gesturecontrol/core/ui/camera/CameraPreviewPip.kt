package com.gesturecontrol.core.ui.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.hand.HandDetectionResult

private val PipWidth = 120.dp
private val PipHeight = 160.dp
private val PipCornerRadius = 12.dp

@Composable
fun CameraPreviewPip(
    surfaceRequest: SurfaceRequest?,
    handDetectionResult: HandDetectionResult,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = PipWidth, height = PipHeight)
            .clip(RoundedCornerShape(PipCornerRadius))
            .background(Color.Black)
            .border(2.dp, Color.White, RoundedCornerShape(PipCornerRadius)),
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.size(width = PipWidth, height = PipHeight),
            )
        }

        HandLandmarkOverlay(
            handDetectionResult = handDetectionResult,
            mirrored = mirrored,
            modifier = Modifier.size(width = PipWidth, height = PipHeight),
        )
    }
}
