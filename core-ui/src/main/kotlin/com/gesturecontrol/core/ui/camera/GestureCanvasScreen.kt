package com.gesturecontrol.core.ui.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gesturecontrol.domain.hand.HandDetectionResult

@Composable
fun GestureCanvasScreen(
    surfaceRequest: SurfaceRequest?,
    handDetectionResult: HandDetectionResult,
    mirrored: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }

        HandLandmarkOverlay(
            handDetectionResult = handDetectionResult,
            mirrored = mirrored,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
