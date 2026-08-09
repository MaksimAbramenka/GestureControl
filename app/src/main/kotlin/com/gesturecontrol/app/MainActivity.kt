package com.gesturecontrol.app

import android.Manifest
import android.content.Context
import android.content.Intent
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gesturecontrol.core.camera.CameraController
import com.gesturecontrol.core.engine.NativeEngine
import com.gesturecontrol.core.engine.submit
import com.gesturecontrol.core.ml.HandLandmarkerAnalyzer
import com.gesturecontrol.core.ml.classifier.GestureClassifier
import com.gesturecontrol.core.ml.training.RecordingProgressStore
import com.gesturecontrol.core.ml.training.TrainingDataRecorder
import com.gesturecontrol.core.ui.camera.BRUSH_COLOR_OPTIONS
import com.gesturecontrol.core.ui.camera.BrushControls
import com.gesturecontrol.core.ui.camera.BrushSizeOption
import com.gesturecontrol.core.ui.camera.DataCollectionControls
import com.gesturecontrol.core.ui.camera.GestureCanvasScreen
import com.gesturecontrol.core.ui.engine.NativeCanvasSurface
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.gesture.GestureInputMapper
import com.gesturecontrol.domain.gesture.GestureSmoother
import com.gesturecontrol.domain.gesture.HandFeatureExtractor
import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.ViewportDimensions
import com.gesturecontrol.domain.hand.toViewportNormalizedPoint
import java.io.File

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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (hasCameraPermission) {
                GestureControlHost()
            } else {
                CameraPermissionRationale {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }
}

@Composable
private fun GestureControlHost() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraController = remember { CameraController(context) }
    val analyzer = remember { HandLandmarkerAnalyzer(context, isFrontCamera = true) }
    val trainingDataRecorder = remember { TrainingDataRecorder(context) }
    val recordingProgressStore = remember { RecordingProgressStore(context) }
    val gestureClassifier = remember { GestureClassifier(context) }
    val gestureSmoother = remember { GestureSmoother() }
    val gestureInputMapper = remember { GestureInputMapper() }
    val nativeEngine = remember { NativeEngine() }

    DisposableEffect(cameraController, analyzer, gestureClassifier) {
        onDispose {
            cameraController.unbindAndAwaitIdle()
            analyzer.close()
            gestureClassifier.close()
        }
    }

    LaunchedEffect(cameraController, analyzer) {
        cameraController.bindToLifecycle(lifecycleOwner, analyzer)
    }

    val surfaceRequest by cameraController.surfaceRequests.collectAsState()
    val handDetectionResult by analyzer.results.collectAsState(EMPTY_HAND_DETECTION_RESULT)

    var appMode by remember { mutableStateOf(AppMode.DRAWING) }
    var selectedGestureClass by remember { mutableStateOf<GestureClass?>(null) }
    var recordedRowCount by remember { mutableIntStateOf(trainingDataRecorder.recordedRowCount) }
    var recordingProgress by remember { mutableStateOf(recordingProgressStore.load()) }
    var currentGesture by remember { mutableStateOf<GestureClass?>(null) }
    var selectedBrushColor by remember { mutableStateOf(BRUSH_COLOR_OPTIONS.first()) }
    var selectedBrushSize by remember { mutableStateOf(BrushSizeOption.MEDIUM) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var showCameraPreview by remember { mutableStateOf(true) }

    SideEffect {
        val hand = handDetectionResult.hands.firstOrNull()
        if (hand == null) {
            currentGesture = null
        } else {
            val features = HandFeatureExtractor.extractFeatures(hand)

            val recordingClass = selectedGestureClass
            if (appMode == AppMode.DATA_COLLECTION && recordingClass != null) {
                trainingDataRecorder.record(recordingClass, features)
                recordedRowCount++

                val handedness = hand.handedness
                if (handedness != null) {
                    recordingProgress = recordingProgress.increment(recordingClass, handedness)
                    recordingProgressStore.save(recordingProgress)
                }
            }

            val classified = gestureClassifier.classify(features)
            currentGesture = gestureSmoother.smooth(classified.gestureClass)
        }

        if (appMode == AppMode.DRAWING) {
            // The camera preview is center-cropped to fill the screen, so the raw camera-image-
            // normalized fingertip position must be mapped through the same crop before it's used
            // as a drawing position, or it drifts from the visible fingertip away from center.
            val fingertip = if (hand != null && viewportSize.width > 0 && viewportSize.height > 0) {
                hand.indexFingertip.toViewportNormalizedPoint(
                    imageDimensions = handDetectionResult.imageDimensions,
                    viewportDimensions = ViewportDimensions(
                        width = viewportSize.width.toFloat(),
                        height = viewportSize.height.toFloat(),
                    ),
                    mirrored = false,
                )
            } else {
                null
            }

            val commands = gestureInputMapper.map(
                gestureClass = currentGesture,
                fingertip = fingertip,
                timestampMs = handDetectionResult.timestampMs,
            )
            commands.forEach { nativeEngine.submit(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it },
    ) {
        GestureCanvasScreen(
            surfaceRequest = surfaceRequest,
            handDetectionResult = handDetectionResult,
            currentGesture = currentGesture,
            mirrored = false,
            showCameraPreview = appMode == AppMode.DATA_COLLECTION || showCameraPreview,
            modifier = Modifier.fillMaxSize(),
        )

        if (appMode == AppMode.DRAWING) {
            NativeCanvasSurface(
                nativeEngine = nativeEngine,
                modifier = Modifier.fillMaxSize(),
            )

            BrushControls(
                selectedColor = selectedBrushColor,
                selectedSize = selectedBrushSize,
                onSelectColor = { option ->
                    selectedBrushColor = option
                    nativeEngine.nativeSetBrushColor(option.r, option.g, option.b)
                },
                onSelectSize = { option ->
                    selectedBrushSize = option
                    nativeEngine.nativeSetBrushSize(option.size)
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
            )

            Button(
                onClick = { showCameraPreview = !showCameraPreview },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp),
            ) {
                Text(if (showCameraPreview) "Hide camera" else "Show camera")
            }
        }

        if (appMode == AppMode.DATA_COLLECTION) {
            DataCollectionControls(
                selectedGestureClass = selectedGestureClass,
                recordedRowCount = recordedRowCount,
                recordingProgress = recordingProgress,
                onSelectGestureClass = { selectedGestureClass = it },
                onShareCsv = { shareTrainingDataCsv(context) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            )
        }

        Button(
            onClick = {
                appMode = if (appMode == AppMode.DRAWING) AppMode.DATA_COLLECTION else AppMode.DRAWING
                selectedGestureClass = null
            },
            modifier = if (appMode == AppMode.DATA_COLLECTION) {
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 48.dp, end = 16.dp)
            } else {
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            },
        ) {
            Text(if (appMode == AppMode.DRAWING) "Data collection" else "Drawing")
        }
    }
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

private fun shareTrainingDataCsv(context: Context) {
    val file = File(context.getExternalFilesDir(null), TrainingDataRecorder.DEFAULT_FILE_NAME)
    if (!file.exists()) return

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share training data"))
}

private val EMPTY_HAND_DETECTION_RESULT = HandDetectionResult(
    hands = emptyList(),
    timestampMs = 0L,
    imageDimensions = ImageDimensions(width = 1, height = 1),
    fps = 0f,
)
