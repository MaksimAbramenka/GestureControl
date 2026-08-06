package com.gesturecontrol.core.ui.engine

import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.gesturecontrol.core.engine.NativeEngine

@Composable
fun NativeCanvasSurface(
    nativeEngine: NativeEngine,
    modifier: Modifier = Modifier,
) {
    val renderLoop = remember(nativeEngine) { NativeRenderLoop(nativeEngine) }

    DisposableEffect(renderLoop) {
        onDispose { renderLoop.stop() }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            SurfaceView(context).apply {
                setZOrderOnTop(true)
                holder.setFormat(PixelFormat.TRANSLUCENT)
                holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            nativeEngine.nativeInit(holder.surface)
                            renderLoop.start()
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            renderLoop.stop()
                        }
                    },
                )
            }
        },
    )
}

private class NativeRenderLoop(
    private val nativeEngine: NativeEngine,
) : Choreographer.FrameCallback {
    private var running = false

    fun start() {
        if (running) return

        running = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return

        nativeEngine.nativeRenderFrame()
        Choreographer.getInstance().postFrameCallback(this)
    }
}
