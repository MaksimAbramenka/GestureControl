package com.gesturecontrol.iosshared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.window.ComposeUIViewController
import com.gesturecontrol.core.engine.ios.GestureCanvasView
import com.gesturecontrol.core.ui.camera.BRUSH_COLOR_OPTIONS
import com.gesturecontrol.core.ui.camera.BrushControls
import com.gesturecontrol.core.ui.camera.BrushSizeOption
import com.gesturecontrol.core.ui.camera.FpsLabel
import com.gesturecontrol.core.ui.camera.GestureStateLabel
import platform.Foundation.NSBundle
import platform.UIKit.UIViewController

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    var selectedColor by remember { mutableStateOf(BRUSH_COLOR_OPTIONS.first()) }
    var selectedSize by remember { mutableStateOf(BrushSizeOption.MEDIUM) }

    val canvasView = remember {
        GestureCanvasView().apply {
            setBrushColor(selectedColor.r, selectedColor.g, selectedColor.b)
            setBrushSize(selectedSize.size)
        }
    }
    val gesturePipeline = remember { GesturePipeline(canvasView, handLandmarkerModelPath()) }
    DisposableEffect(gesturePipeline) {
        onDispose { gesturePipeline.stop() }
    }
    LaunchedEffect(gesturePipeline) {
        gesturePipeline.start()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
        UIKitView(
            factory = { canvasView },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FpsLabel(fps = gesturePipeline.fps)
            GestureStateLabel(gestureClass = gesturePipeline.gestureClass)
        }
        BrushControls(
            selectedColor = selectedColor,
            selectedSize = selectedSize,
            onSelectColor = { option ->
                selectedColor = option
                canvasView.setBrushColor(option.r, option.g, option.b)
            },
            onSelectSize = { option ->
                selectedSize = option
                canvasView.setBrushSize(option.size)
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        )
    }
}

/** hand_landmarker.task is bundled at the app root by xcodegen (iosApp/project.yml) -- see
 * Stage 6d-1. A missing resource here means the build/packaging is broken, not a runtime
 * condition to recover from. */
private fun handLandmarkerModelPath(): String =
    NSBundle.mainBundle.pathForResource(name = "hand_landmarker", ofType = "task")
        ?: error("hand_landmarker.task missing from the app bundle")
