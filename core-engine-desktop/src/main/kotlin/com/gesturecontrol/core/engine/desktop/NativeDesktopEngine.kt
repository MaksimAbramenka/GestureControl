package com.gesturecontrol.core.engine.desktop

/**
 * JNI bridge into the shared native core (`core-engine/src/main/cpp`) for the desktop target --
 * see `desktop-shim/DesktopRendererBridge.cpp` for the native side. Unlike Android's
 * `NativeEngine` (global singleton state behind the JNI boundary) this exposes opaque handles
 * directly, matching iOS's own per-instance `GCSceneGraph`/`GCRenderer` pattern -- convenient for
 * isolated tests, and there's no Android-style Activity-lifecycle singleton to mirror here.
 *
 * Requires a desktop OpenGL context to already be current on the calling thread before
 * [nativeRendererInit]/[nativeRendererDraw]/[nativeRendererCapture] are called -- this object
 * creates no context of its own (see DesktopRendererBridge.cpp's own top comment).
 */
object NativeDesktopEngine {
    init {
        // Set by the :core-engine-desktop Gradle build (buildNativeLibDesktop task output) rather
        // than relying on java.library.path -- avoids needing extra JVM args to find the .dylib
        // during Gradle-run tests or, later, the packaged desktop app (Stage 6's concern, not
        // this one). Packaging this properly into app-desktop's own distribution happens there.
        val explicitPath = System.getProperty("gesture.canvas.desktop.native.lib")
        if (explicitPath != null) {
            System.load(explicitPath)
        } else {
            System.loadLibrary("gesture_canvas_core_desktop")
        }
    }

    external fun nativeSceneCreate(): Long

    external fun nativeSceneDestroy(handle: Long)

    external fun nativeSceneSetBrushColor(handle: Long, r: Float, g: Float, b: Float)

    external fun nativeSceneSetBrushSize(handle: Long, size: Float)

    external fun nativeSceneSubmitInput(
        handle: Long,
        x: Float,
        y: Float,
        state: Int,
        pressure: Float,
        timestampMs: Long,
    )

    external fun nativeRendererCreate(): Long

    external fun nativeRendererDestroy(handle: Long)

    external fun nativeRendererInit(handle: Long): Boolean

    external fun nativeRendererCreateOffscreenTarget(handle: Long, width: Int, height: Int): Boolean

    external fun nativeRendererResize(handle: Long, width: Int, height: Int)

    external fun nativeRendererDraw(rendererHandle: Long, sceneHandle: Long)

    external fun nativeRendererCapture(width: Int, height: Int): ByteArray?
}
