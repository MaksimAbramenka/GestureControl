#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <mutex>
#include <vector>

#include "input/InputEvent.h"
#include "render/EglContext.h"
#include "render/StrokeRenderer.h"
#include "scene/SceneGraph.h"

#define LOG_TAG "GestureCanvasCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using gesture_canvas::EglContext;
using gesture_canvas::InputEvent;
using gesture_canvas::SceneGraph;
using gesture_canvas::StrokeRenderer;

namespace {

    EglContext gEglContext;
    StrokeRenderer gRenderer;
    SceneGraph gScene;
    bool gRendererInitialized = false;

    std::mutex gInputQueueMutex;
    std::vector<InputEvent> gInputQueue;

// Drains the thread-safe input queue on the calling (render) thread, per the
// plan's chosen threading pattern: nativeSubmitInput may be called from the
// MediaPipe callback thread, nativeRenderFrame always runs on the render
// thread and is the only place the queue is consumed.
    std::vector<InputEvent> drainInputQueue() {
        std::lock_guard<std::mutex> lock(gInputQueueMutex);
        std::vector<InputEvent> drained;
        drained.swap(gInputQueue);
        return drained;
    }

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeInit(
        JNIEnv *env, jobject /* thiz */, jobject surface) {
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (window == nullptr) {
        LOGE("ANativeWindow_fromSurface returned null");
        return;
    }

    if (!gEglContext.init(window)) {
        LOGE("EglContext init failed");
        return;
    }

    if (!gEglContext.makeCurrent()) {
        LOGE("eglMakeCurrent failed");
        return;
    }

    gRenderer.resize(gEglContext.width(), gEglContext.height());
    gRendererInitialized = gRenderer.init();
    if (!gRendererInitialized) {
        LOGE("StrokeRenderer init failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeRenderFrame(
        JNIEnv *env, jobject /* thiz */) {
    if (!gRendererInitialized || !gEglContext.isValid()) {
        return;
    }

    for (const auto &event: drainInputQueue()) {
        gScene.submitInput(event);
    }

    if (!gEglContext.makeCurrent()) {
        LOGE("eglMakeCurrent failed in nativeRenderFrame");
        return;
    }

    gRenderer.draw(gScene.visibleStrokes());
    gEglContext.swapBuffers();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeSubmitInput(
        JNIEnv *env, jobject /* thiz */, jfloat x, jfloat y, jint state, jfloat pressure,
        jlong timestampMs) {
    InputEvent event{
            x, y, static_cast<InputEvent::State>(state), pressure, timestampMs,
    };
    std::lock_guard<std::mutex> lock(gInputQueueMutex);
    gInputQueue.push_back(event);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeSetBrushColor(
        JNIEnv *env, jobject /* thiz */, jfloat r, jfloat g, jfloat b) {
    gScene.setBrushColor(r, g, b);
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeSetBrushSize(
        JNIEnv *env, jobject /* thiz */, jfloat size) {
    gScene.setBrushSize(size);
}
