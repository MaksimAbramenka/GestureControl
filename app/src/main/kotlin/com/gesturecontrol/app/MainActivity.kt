package com.gesturecontrol.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gesturecontrol.core.camera.CameraController
import com.gesturecontrol.core.ml.HandLandmarkerAnalyzer
import com.gesturecontrol.core.ui.camera.GestureCanvasScreen
import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.ImageDimensions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GestureControlApp()
            }
        }
    }
}

@Composable
private fun GestureControlApp() {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (hasCameraPermission) {
                GestureCanvasHost()
            } else {
                CameraPermissionRationale(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                )
            }
        }
    }
}

@Composable
private fun GestureCanvasHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController = remember { CameraController(context) }
    val analyzer = remember { HandLandmarkerAnalyzer(context, isFrontCamera = true) }

    DisposableEffect(analyzer) {
        onDispose { analyzer.close() }
    }

    LaunchedEffect(cameraController, analyzer) {
        cameraController.bindToLifecycle(lifecycleOwner, analyzer)
    }

    val surfaceRequest by cameraController.surfaceRequests.collectAsState()
    val handDetectionResult by analyzer.results.collectAsState(EMPTY_HAND_DETECTION_RESULT)

    GestureCanvasScreen(
        surfaceRequest = surfaceRequest,
        handDetectionResult = handDetectionResult,
        // Mirroring is already baked into HandLandmarkerAnalyzer's output (isFrontCamera above) —
        // mirroring again here would cancel it back out.
        mirrored = false,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun CameraPermissionRationale(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Camera access is required to track hand gestures.")
            Button(onClick = onRequestPermission) {
                Text("Grant camera permission")
            }
        }
    }
}

private val EMPTY_HAND_DETECTION_RESULT =
    HandDetectionResult(
        hands = emptyList(),
        timestampMs = 0L,
        imageDimensions = ImageDimensions(width = 1, height = 1),
    )
