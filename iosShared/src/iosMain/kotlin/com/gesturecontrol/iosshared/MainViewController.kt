package com.gesturecontrol.iosshared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.gesturecontrol.domain.gesture.GestureClass
import platform.UIKit.UIViewController

@Suppress("FunctionName")
fun MainViewController(): UIViewController = ComposeUIViewController {
    var selectedColor by remember { mutableStateOf(BRUSH_COLOR_OPTIONS.first()) }
    var selectedSize by remember { mutableStateOf(BrushSizeOption.MEDIUM) }

    Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray)) {
        UIKitView(
            factory = { GestureCanvasView() },
            modifier = Modifier.fillMaxSize(),
        )
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FpsLabel(fps = 24f)
            GestureStateLabel(gestureClass = GestureClass.DRAW)
        }
        BrushControls(
            selectedColor = selectedColor,
            selectedSize = selectedSize,
            onSelectColor = { selectedColor = it },
            onSelectSize = { selectedSize = it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
        )
    }
}
