package com.gesturecontrol.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.gesturecontrol.core.camera.CameraController
import com.gesturecontrol.core.engine.NativeEngine
import com.gesturecontrol.core.engine.submit
import com.gesturecontrol.core.ml.HandLandmarkerAnalyzer
import com.gesturecontrol.core.ml.classifier.GestureClassifier
import com.gesturecontrol.core.ml.training.RecordingProgressStore
import com.gesturecontrol.core.ml.training.TrainingDataRecorder
import com.gesturecontrol.core.ui.camera.BRUSH_COLOR_OPTIONS
import com.gesturecontrol.core.ui.camera.BrushColorOption
import com.gesturecontrol.core.ui.camera.BrushControls
import com.gesturecontrol.core.ui.camera.BrushSizeOption
import com.gesturecontrol.core.ui.camera.CarouselController
import com.gesturecontrol.core.ui.camera.DataCollectionControls
import com.gesturecontrol.core.ui.camera.DraggableCameraPreview
import com.gesturecontrol.core.ui.camera.GestureCanvasScreen
import com.gesturecontrol.core.ui.camera.GestureCursorOverlay
import com.gesturecontrol.core.ui.camera.PIP_ASPECT_RATIO
import com.gesturecontrol.core.ui.camera.PIP_DEFAULT_SIZE_FRACTION
import com.gesturecontrol.core.ui.camera.VoiceActivationLabel
import com.gesturecontrol.core.ui.engine.NativeCanvasSurface
import com.gesturecontrol.core.voice.SpeechRecognizerSource
import com.gesturecontrol.core.voice.VoiceActivationOrchestrator
import com.gesturecontrol.core.voice.VoiceActivationResult
import com.gesturecontrol.core.voice.VoiceCommandClassifier
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.gesture.GestureInputMapper
import com.gesturecontrol.domain.gesture.GestureSmoother
import com.gesturecontrol.domain.gesture.HandFeatureExtractor
import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import com.gesturecontrol.domain.hand.PositionSmoother
import com.gesturecontrol.domain.hand.ViewportDimensions
import com.gesturecontrol.domain.hand.toViewportNormalizedPoint
import com.gesturecontrol.domain.training.RecordingProgress
import com.gesturecontrol.domain.ui.DwellZone
import com.gesturecontrol.domain.ui.EdgeDwellStepper
import com.gesturecontrol.domain.voice.Command
import com.gesturecontrol.domain.voice.PointHoldGate
import com.gesturecontrol.domain.voice.VoiceActivationController
import com.gesturecontrol.domain.voice.VoiceActivationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private val CameraToggleButtonWidth = 112.dp

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
    val cursorSmoother = remember { PositionSmoother() }
    val nativeEngine = remember { NativeEngine() }
    val edgeDwellStepper = remember { EdgeDwellStepper() }
    val pointHoldGate = remember { PointHoldGate() }
    val voiceActivationController = remember { VoiceActivationController() }
    val speechRecognizerSource = remember { SpeechRecognizerSource(context) }
    val voiceCommandClassifier = remember { VoiceCommandClassifier(context) }
    val voiceActivationOrchestrator = remember(speechRecognizerSource, voiceCommandClassifier) {
        VoiceActivationOrchestrator(speechRecognizerSource, voiceCommandClassifier)
    }
    val coroutineScope = rememberCoroutineScope()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        voiceCommandClassifier.initialize()
    }

    DisposableEffect(cameraController, analyzer, gestureClassifier, voiceCommandClassifier) {
        onDispose {
            cameraController.unbindAndAwaitIdle()
            analyzer.close()
            gestureClassifier.close()
            voiceCommandClassifier.close()
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
    var showClearCanvasConfirmation by remember { mutableStateOf(false) }
    var cursorPosition by remember { mutableStateOf<NormalizedPoint?>(null) }
    var pipOffset by remember { mutableStateOf<Offset?>(null) }
    var pipSizeFraction by remember { mutableStateOf(PIP_DEFAULT_SIZE_FRACTION) }
    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var colorCarouselController by remember { mutableStateOf<CarouselController?>(null) }
    var sizeCarouselController by remember { mutableStateOf<CarouselController?>(null) }
    var colorCarouselActiveEdge by remember { mutableStateOf<Int?>(null) }
    var sizeCarouselActiveEdge by remember { mutableStateOf<Int?>(null) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var voiceActivationState by remember { mutableStateOf<VoiceActivationState>(VoiceActivationState.Idle) }
    var lastVoiceTranscript by remember { mutableStateOf<String?>(null) }
    var lastVoiceCommand by remember { mutableStateOf<Command?>(null) }
    val lastProcessedTimestampMs = remember { longArrayOf(-1L) }

    fun selectBrushColor(option: BrushColorOption) {
        selectedBrushColor = option
        nativeEngine.nativeSetBrushColor(option.r, option.g, option.b)
    }

    fun selectBrushSize(option: BrushSizeOption) {
        selectedBrushSize = option
        nativeEngine.nativeSetBrushSize(option.size)
    }

    fun clearTrainingData() {
        trainingDataRecorder.clear()
        recordingProgressStore.clear()
        recordedRowCount = 0
        recordingProgress = RecordingProgress()
    }

    fun saveAndShareDrawing() {
        val strokes = nativeEngine.captureSnapshot(viewportSize.width, viewportSize.height) ?: return
        val bitmap = flattenOnWhite(strokes)
        strokes.recycle()
        coroutineScope.launch(Dispatchers.IO) {
            val file = writeDrawingPng(context, bitmap)
            bitmap.recycle()
            if (file != null) {
                withContext(Dispatchers.Main) { shareDrawing(context, file) }
            }
        }
    }

    fun applyVoiceCommand(command: Command) {
        when (command) {
            is Command.SetBrushColor -> {
                BRUSH_COLOR_OPTIONS
                    .firstOrNull { it.label.equals(command.color.name, ignoreCase = true) }
                    ?.let(::selectBrushColor)
            }

            is Command.SetBrushSize -> {
                BrushSizeOption.entries
                    .firstOrNull { it.name == command.size.name }
                    ?.let(::selectBrushSize)
            }

            Command.Undo -> {
                nativeEngine.nativeUndo()
                canUndo = nativeEngine.nativeCanUndo()
                canRedo = nativeEngine.nativeCanRedo()
            }

            Command.Redo -> {
                nativeEngine.nativeRedo()
                canUndo = nativeEngine.nativeCanUndo()
                canRedo = nativeEngine.nativeCanRedo()
            }

            Command.Clear -> showClearCanvasConfirmation = true
            Command.Save -> saveAndShareDrawing()
            Command.StartContinuousListening, Command.StopContinuousListening, Command.Unrecognized -> Unit
        }
    }

    suspend fun runOneVoiceActivationCycle() {
        when (val result = voiceActivationOrchestrator.runOnce()) {
            is VoiceActivationResult.Heard -> {
                lastVoiceTranscript = result.transcript
                lastVoiceCommand = result.command
                applyVoiceCommand(result.command)
                voiceActivationController.onCommandCaptured(result.command)
            }

            VoiceActivationResult.CaptureFailed -> {
                lastVoiceTranscript = null
                lastVoiceCommand = null
                voiceActivationController.onListeningTimeout()
            }
        }
        voiceActivationState = voiceActivationController.state
    }

    LaunchedEffect(voiceActivationState is VoiceActivationState.ContinuousListening) {
        while (voiceActivationState is VoiceActivationState.ContinuousListening) {
            runOneVoiceActivationCycle()
        }
    }

    SideEffect {
        if (handDetectionResult.timestampMs == lastProcessedTimestampMs[0]) return@SideEffect

        lastProcessedTimestampMs[0] = handDetectionResult.timestampMs

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

        // Fed every frame regardless of mode so its hold timer never sees a stale timestamp
        // jump from a mode switch -- only acted on in Drawing mode, below, so a POINT held while
        // recording training data (which happens constantly) never triggers voice listening.
        val pointHoldTriggered = pointHoldGate.onFrame(currentGesture, handDetectionResult.timestampMs)

        if (appMode == AppMode.DRAWING) {
            if (pointHoldTriggered) {
                voiceActivationController.onPointHoldTriggered()
                voiceActivationState = voiceActivationController.state
                coroutineScope.launch { runOneVoiceActivationCycle() }
            }

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
            cursorPosition = cursorSmoother.smooth(fingertip)

            // Hovering the fingertip over a carousel's left/right edge band for a beat steps its
            // selection, since reaching a small on-screen picker with an in-air gesture like a
            // fling is unreliable -- direction and precision are much easier to get right this
            // way, entirely from cursor position with no gesture classification involved.
            val activeEdge = resolveActiveCarouselEdge(
                cursorPosition = cursorPosition,
                viewportSize = viewportSize,
                rootCoordinates = rootCoordinates,
                colorCarouselController = colorCarouselController,
                sizeCarouselController = sizeCarouselController,
            )
            colorCarouselActiveEdge = activeEdge?.takeIf { it.controller === colorCarouselController }?.direction
            sizeCarouselActiveEdge = activeEdge?.takeIf { it.controller === sizeCarouselController }?.direction
            if (edgeDwellStepper.onFrame(activeEdge?.zone, handDetectionResult.timestampMs)) {
                val controller = activeEdge!!.controller
                val direction = activeEdge.direction
                coroutineScope.launch { controller.step(direction) }
            }

            // Drawing/erasing is suppressed while the fingertip is over the PiP camera preview,
            // so the preview can be interacted with (or just sit there) without leaving a stroke.
            val isFingertipOverPip = fingertip != null &&
                showCameraPreview &&
                isPointOverPip(fingertip, viewportSize, pipOffset, pipSizeFraction)
            val effectiveGestureClass = if (isFingertipOverPip) GestureClass.HOVER else currentGesture

            val commands = gestureInputMapper.map(
                gestureClass = effectiveGestureClass,
                fingertip = fingertip,
                timestampMs = handDetectionResult.timestampMs,
            )
            commands.forEach { nativeEngine.submit(it) }
            canUndo = nativeEngine.nativeCanUndo()
            canRedo = nativeEngine.nativeCanRedo()
        } else {
            cursorPosition = cursorSmoother.smooth(null)
            colorCarouselActiveEdge = null
            sizeCarouselActiveEdge = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .onGloballyPositioned { rootCoordinates = it },
    ) {
        GestureCanvasScreen(
            surfaceRequest = surfaceRequest,
            handDetectionResult = handDetectionResult,
            currentGesture = currentGesture,
            mirrored = false,
            showFullscreenCamera = appMode == AppMode.DATA_COLLECTION,
            modifier = Modifier.fillMaxSize(),
        )

        if (appMode == AppMode.DRAWING) {
            NativeCanvasSurface(
                nativeEngine = nativeEngine,
                modifier = Modifier.fillMaxSize(),
            )

            GestureCursorOverlay(
                fingertip = cursorPosition,
                gestureClass = currentGesture,
                brushColor = selectedBrushColor.composeColor,
                modifier = Modifier.fillMaxSize(),
            )

            VoiceActivationLabel(
                activationState = voiceActivationState,
                lastTranscript = lastVoiceTranscript,
                lastCommand = lastVoiceCommand,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
            )

            BrushControls(
                selectedColor = selectedBrushColor,
                selectedSize = selectedBrushSize,
                onSelectColor = ::selectBrushColor,
                onSelectSize = ::selectBrushSize,
                onColorCarouselControllerReady = { colorCarouselController = it },
                onSizeCarouselControllerReady = { sizeCarouselController = it },
                colorCarouselActiveEdge = colorCarouselActiveEdge,
                sizeCarouselActiveEdge = sizeCarouselActiveEdge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 40.dp, start = 16.dp),
            )

            Button(
                onClick = { showCameraPreview = !showCameraPreview },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 40.dp, end = 16.dp)
                    .width(CameraToggleButtonWidth),
            ) {
                Text(
                    text = if (showCameraPreview) "Hide\ncamera" else "Show\ncamera",
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 112.dp, end = 16.dp),
            ) {
                FilledIconButton(
                    onClick = {
                        nativeEngine.nativeUndo()
                        canUndo = nativeEngine.nativeCanUndo()
                        canRedo = nativeEngine.nativeCanRedo()
                    },
                    enabled = canUndo,
                ) {
                    Text("↩", fontSize = 20.sp)
                }

                FilledIconButton(
                    onClick = {
                        nativeEngine.nativeRedo()
                        canUndo = nativeEngine.nativeCanUndo()
                        canRedo = nativeEngine.nativeCanRedo()
                    },
                    enabled = canRedo,
                ) {
                    Text("↪", fontSize = 20.sp)
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 168.dp, end = 16.dp),
            ) {
                FilledIconButton(onClick = ::saveAndShareDrawing) {
                    Icon(Icons.Filled.Share, contentDescription = "Share drawing")
                }

                FilledIconButton(
                    onClick = { showClearCanvasConfirmation = true },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = androidx.compose.ui.graphics.Color(0xFF8B0000),
                    ),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear canvas")
                }
            }
        }

        if (showClearCanvasConfirmation) {
            AlertDialog(
                onDismissRequest = { showClearCanvasConfirmation = false },
                title = { Text("Clear the canvas?") },
                text = {
                    Text(
                        "This erases everything you've drawn, including your undo history. This can't be undone.",
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showClearCanvasConfirmation = false
                        nativeEngine.nativeClearCanvas()
                        canUndo = nativeEngine.nativeCanUndo()
                        canRedo = nativeEngine.nativeCanRedo()
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearCanvasConfirmation = false }) {
                        Text("Cancel")
                    }
                },
            )
        }

        if (appMode == AppMode.DATA_COLLECTION) {
            DataCollectionControls(
                selectedGestureClass = selectedGestureClass,
                recordedRowCount = recordedRowCount,
                recordingProgress = recordingProgress,
                onSelectGestureClass = { selectedGestureClass = it },
                onShareCsv = { shareTrainingDataCsv(context) },
                onClearData = ::clearTrainingData,
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

        if (appMode == AppMode.DRAWING && showCameraPreview) {
            DraggableCameraPreview(
                surfaceRequest = surfaceRequest,
                handDetectionResult = handDetectionResult,
                mirrored = false,
                viewportSizePx = viewportSize,
                offset = pipOffset,
                onOffsetChange = { pipOffset = it },
                sizeFraction = pipSizeFraction,
                onSizeFractionChange = { pipSizeFraction = it },
                modifier = Modifier.fillMaxSize(),
            )
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

private data class ActiveCarouselEdge(
    val controller: CarouselController,
    val direction: Int,
    val zone: DwellZone,
)

private fun resolveActiveCarouselEdge(
    cursorPosition: NormalizedPoint?,
    viewportSize: IntSize,
    rootCoordinates: LayoutCoordinates?,
    colorCarouselController: CarouselController?,
    sizeCarouselController: CarouselController?,
): ActiveCarouselEdge? {
    if (cursorPosition == null || rootCoordinates == null) return null

    val pointPx = Offset(cursorPosition.x * viewportSize.width, cursorPosition.y * viewportSize.height)
    colorCarouselController?.edgeZoneAt(rootCoordinates, pointPx)?.let { direction ->
        return ActiveCarouselEdge(colorCarouselController, direction, DwellZone("color:$direction"))
    }
    sizeCarouselController?.edgeZoneAt(rootCoordinates, pointPx)?.let { direction ->
        return ActiveCarouselEdge(sizeCarouselController, direction, DwellZone("size:$direction"))
    }
    return null
}

private fun isPointOverPip(
    point: NormalizedPoint,
    viewportSize: IntSize,
    pipOffset: Offset?,
    pipSizeFraction: Float,
): Boolean {
    if (pipOffset == null) return false

    val width = pipSizeFraction * viewportSize.width
    val height = width * PIP_ASPECT_RATIO
    val x = point.x * viewportSize.width
    val y = point.y * viewportSize.height
    return x in pipOffset.x..(pipOffset.x + width) && y in pipOffset.y..(pipOffset.y + height)
}

private fun flattenOnWhite(strokes: Bitmap): Bitmap {
    val flattened = createBitmap(strokes.width, strokes.height)
    Canvas(flattened).apply {
        drawColor(Color.WHITE)
        drawBitmap(strokes, 0f, 0f, null)
    }
    return flattened
}

private fun writeDrawingPng(context: Context, bitmap: Bitmap): File? {
    val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: return null
    picturesDir.mkdirs()
    val file = File(picturesDir, "drawing_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file
    }.getOrNull()
}

private fun shareDrawing(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share drawing"))
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
