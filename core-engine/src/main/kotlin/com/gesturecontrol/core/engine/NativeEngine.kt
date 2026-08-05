package com.gesturecontrol.core.engine

import android.view.Surface

class NativeEngine {
    companion object {
        init {
            System.loadLibrary("gesture_canvas_core")
        }
    }

    external fun nativeInit(surface: Surface)

    external fun nativeRenderFrame()

    external fun nativeSubmitInput(
        x: Float,
        y: Float,
        state: Int,
        pressure: Float,
        timestampMs: Long,
    )

    external fun nativeSetBrushColor(
        r: Float,
        g: Float,
        b: Float,
    )

    external fun nativeSetBrushSize(size: Float)
}
