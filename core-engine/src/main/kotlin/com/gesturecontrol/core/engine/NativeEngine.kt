package com.gesturecontrol.core.engine

import android.graphics.Bitmap
import android.view.Surface
import java.nio.ByteBuffer

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

    external fun nativeClearCanvas()

    external fun nativeCaptureSnapshot(width: Int, height: Int): ByteArray?

    fun captureSnapshot(width: Int, height: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val pixels = nativeCaptureSnapshot(width, height) ?: return null

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            copyPixelsFromBuffer(ByteBuffer.wrap(pixels))
        }
    }
}
