#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <mutex>
#include <vector>

#include "input/InputEvent.h"
#include "render/EglContext.h"
#include "render/TriangleRenderer.h"

#define LOG_TAG "GestureCanvasCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using gesture_canvas::EglContext;
using gesture_canvas::InputEvent;
using gesture_canvas::TriangleRenderer;

namespace {

EglContext gEglContext;
TriangleRenderer gRenderer;
bool gRendererInitialized = false;

std::mutex gInputQueueMutex;
std::vector<InputEvent> gInputQueue;

float gBrushColor[3] = {1.0f, 1.0f, 1.0f};
float gBrushSize = 4.0f;

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
        LOGE("TriangleRenderer init failed");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeRenderFrame(
    JNIEnv *env, jobject /* thiz */) {
    if (!gRendererInitialized || !gEglContext.isValid()) {
        return;
    }

    drainInputQueue();

    if (!gEglContext.makeCurrent()) {
        LOGE("eglMakeCurrent failed in nativeRenderFrame");
        return;
    }

    gRenderer.draw();
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
    gBrushColor[0] = r;
    gBrushColor[1] = g;
    gBrushColor[2] = b;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeSetBrushSize(
    JNIEnv *env, jobject /* thiz */, jfloat size) {
    gBrushSize = size;
}
