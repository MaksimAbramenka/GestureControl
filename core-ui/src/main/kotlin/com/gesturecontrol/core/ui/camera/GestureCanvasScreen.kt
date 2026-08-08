package com.gesturecontrol.core.ui.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.hand.HandDetectionResult

@Composable
fun GestureCanvasScreen(
    surfaceRequest: SurfaceRequest?,
    handDetectionResult: HandDetectionResult,
    currentGesture: GestureClass?,
    mirrored: Boolean,
    showCameraPreview: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showCameraPreview) {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
            )
        }

        HandLandmarkOverlay(
            handDetectionResult = handDetectionResult,
            mirrored = mirrored,
            modifier = Modifier.fillMaxSize(),
        )

        FpsLabel(
            fps = handDetectionResult.fps,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        )

        GestureStateLabel(
            gestureClass = currentGesture,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        )
    }
}
