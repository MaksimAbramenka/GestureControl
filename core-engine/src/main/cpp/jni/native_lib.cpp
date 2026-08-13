#include <GLES3/gl3.h>
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include <cstdint>
#include <cstring>
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

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeCaptureSnapshot(
        JNIEnv *env, jobject /* thiz */, jint width, jint height) {
    if (!gRendererInitialized || !gEglContext.isValid() || width <= 0 || height <= 0) {
        return nullptr;
    }

    if (!gEglContext.makeCurrent()) {
        LOGE("eglMakeCurrent failed in nativeCaptureSnapshot");
        return nullptr;
    }

    const int originalWidth = gEglContext.width();
    const int originalHeight = gEglContext.height();

    GLuint resolveFbo = 0;
    GLuint resolveColor = 0;
    glGenFramebuffers(1, &resolveFbo);
    glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
    glGenRenderbuffers(1, &resolveColor);
    glBindRenderbuffer(GL_RENDERBUFFER, resolveColor);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
    glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, resolveColor);
    const bool resolveComplete = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;

    GLuint msaaFbo = 0;
    GLuint msaaColor = 0;
    bool msaaComplete = false;
    if (resolveComplete) {
        glGenFramebuffers(1, &msaaFbo);
        glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
        glGenRenderbuffers(1, &msaaColor);
        glBindRenderbuffer(GL_RENDERBUFFER, msaaColor);
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_RGBA8, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, msaaColor);
        msaaComplete = glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE;
    }

    std::vector<uint8_t> pixels;
    if (resolveComplete) {
        glViewport(0, 0, width, height);
        if (msaaComplete) {
            glBindFramebuffer(GL_FRAMEBUFFER, msaaFbo);
            gRenderer.draw(gScene.visibleStrokes());
            glBindFramebuffer(GL_READ_FRAMEBUFFER, msaaFbo);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT,
                               GL_NEAREST);
        } else {
            glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
            gRenderer.draw(gScene.visibleStrokes());
        }

        glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
        pixels.resize(static_cast<size_t>(width) * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    }

    glBindFramebuffer(GL_FRAMEBUFFER, 0);
    if (msaaColor != 0) glDeleteRenderbuffers(1, &msaaColor);
    if (msaaFbo != 0) glDeleteFramebuffers(1, &msaaFbo);
    if (resolveColor != 0) glDeleteRenderbuffers(1, &resolveColor);
    if (resolveFbo != 0) glDeleteFramebuffers(1, &resolveFbo);
    glViewport(0, 0, originalWidth, originalHeight);

    if (pixels.empty()) {
        return nullptr;
    }

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

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeClearCanvas(
        JNIEnv *env, jobject /* thiz */) {
    gScene.clear();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeUndo(
        JNIEnv *env, jobject /* thiz */) {
    gScene.undo();
}

extern "C" JNIEXPORT void JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeRedo(
        JNIEnv *env, jobject /* thiz */) {
    gScene.redo();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeCanUndo(
        JNIEnv *env, jobject /* thiz */) {
    return gScene.canUndo();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_gesturecontrol_core_engine_NativeEngine_nativeCanRedo(
        JNIEnv *env, jobject /* thiz */) {
    return gScene.canRedo();
}
