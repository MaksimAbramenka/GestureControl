// JNI bridge for the desktop (JVM) target -- the desktop equivalent of jni/native_lib.cpp
// (Android) and ios-shim/GestureCanvasBridge.cpp (iOS). Unlike either of those, this file owns no
// GL context of its own: LWJGL's GLFW binding creates and makes current a real desktop OpenGL
// context directly from Kotlin (see NativeDesktopEngine.kt) before any of these functions are
// called, so by the time a JNI call reaches here, a context is already current on the calling
// thread -- these functions only ever call into the platform-agnostic SceneGraph/StrokeRenderer
// classes core-engine/src/main/cpp already defines, the same classes Android and iOS both use
// unchanged. Opaque jlong handles (rather than Android's global-singleton statics) so Kotlin-side
// tests can create isolated instances per test case, the same reasoning the iOS shim's opaque
// GCSceneGraph*/GCRenderer* handles already use.

#include <jni.h>

#include <cstring>
#include <vector>

#include "render/GLCompat.h"
#include "render/StrokeRenderer.h"
#include "scene/SceneGraph.h"

using gesture_canvas::InputEvent;
using gesture_canvas::SceneGraph;
using gesture_canvas::StrokeRenderer;

namespace {

    inline SceneGraph *sceneFromHandle(jlong handle) {
        return reinterpret_cast<SceneGraph *>(handle);
    }

    inline StrokeRenderer *rendererFromHandle(jlong handle) {
        return reinterpret_cast<StrokeRenderer *>(handle);
    }

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeSceneCreate(
        JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new SceneGraph());
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeSceneDestroy(
        JNIEnv *, jobject, jlong handle) {
    delete sceneFromHandle(handle);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeSceneSetBrushColor(
        JNIEnv *, jobject, jlong handle, jfloat r, jfloat g, jfloat b) {
    sceneFromHandle(handle)->setBrushColor(r, g, b);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeSceneSetBrushSize(
        JNIEnv *, jobject, jlong handle, jfloat size) {
    sceneFromHandle(handle)->setBrushSize(size);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeSceneSubmitInput(
        JNIEnv *, jobject, jlong handle, jfloat x, jfloat y, jint state, jfloat pressure,
        jlong timestampMs) {
    InputEvent event{x, y, static_cast<InputEvent::State>(state), pressure, timestampMs};
    sceneFromHandle(handle)->submitInput(event);
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererCreate(
        JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new StrokeRenderer());
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererDestroy(
        JNIEnv *, jobject, jlong handle) {
    delete rendererFromHandle(handle);
}

// Assumes a desktop GL context is already current on the calling thread (see this file's own
// top comment) -- unlike gc_renderer_init (iOS) or nativeInit (Android), this never creates a
// context itself, only the shader program/VAO/VBO StrokeRenderer::init() itself owns.
extern "C" JNIEXPORT jboolean JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererInit(
        JNIEnv *, jobject, jlong handle) {
    return rendererFromHandle(handle)->init();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererResize(
        JNIEnv *, jobject, jlong handle, jint width, jint height) {
    rendererFromHandle(handle)->resize(width, height);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererDraw(
        JNIEnv *, jobject, jlong rendererHandle, jlong sceneHandle) {
    rendererFromHandle(rendererHandle)->draw(sceneFromHandle(sceneHandle)->visibleStrokes());
}

// Reads back whatever framebuffer is currently bound -- Kotlin/LWJGL owns framebuffer setup on
// desktop (the hidden GLFW window's default framebuffer for this smoke test), the same "read
// back the currently-bound framebuffer" contract gc_renderer_capture (iOS) already documents.
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gesturecontrol_core_engine_desktop_NativeDesktopEngine_nativeRendererCapture(
        JNIEnv *env, jobject, jint width, jint height) {
    if (width <= 0 || height <= 0) {
        return nullptr;
    }

    std::vector<uint8_t> pixels(static_cast<size_t>(width) * height * 4);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());

    // glReadPixels is bottom-up; flip to top-down to match every other platform's capture
    // convention in this codebase (gc_renderer_capture, nativeCaptureSnapshot).
    const auto rowBytes = static_cast<size_t>(width) * 4;
    std::vector<uint8_t> flipped(pixels.size());
    for (int row = 0; row < height; ++row) {
        const uint8_t *src = pixels.data() + static_cast<size_t>(height - 1 - row) * rowBytes;
        uint8_t *dst = flipped.data() + static_cast<size_t>(row) * rowBytes;
        std::memcpy(dst, src, rowBytes);
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(flipped.size()));
    if (result != nullptr) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(flipped.size()),
                                 reinterpret_cast<const jbyte *>(flipped.data()));
    }
    return result;
}
