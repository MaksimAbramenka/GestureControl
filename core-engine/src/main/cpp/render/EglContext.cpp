#include "render/EglContext.h"

#include <android/log.h>

#define LOG_TAG "GestureCanvasCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gesture_canvas {

EglContext::~EglContext() {
    destroy();
}

bool EglContext::init(ANativeWindow *window) {
    destroy();
    window_ = window;

    display_ = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (display_ == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }

    if (!eglInitialize(display_, nullptr, nullptr)) {
        LOGE("eglInitialize failed");
        return false;
    }

    const EGLint configAttribsMsaa[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_SAMPLE_BUFFERS, 1,
        EGL_SAMPLES, 4,
        EGL_NONE,
    };
    const EGLint configAttribsNoMsaa[] = {
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT,
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_NONE,
    };
    EGLConfig config;
    EGLint numConfigs = 0;
    bool haveConfig =
        eglChooseConfig(display_, configAttribsMsaa, &config, 1, &numConfigs) && numConfigs > 0;
    if (!haveConfig) {
        haveConfig =
            eglChooseConfig(display_, configAttribsNoMsaa, &config, 1, &numConfigs) && numConfigs > 0;
    }
    if (!haveConfig) {
        LOGE("eglChooseConfig failed");
        return false;
    }

    const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, 3,
        EGL_NONE,
    };
    context_ = eglCreateContext(display_, config, EGL_NO_CONTEXT, contextAttribs);
    if (context_ == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed");
        return false;
    }

    surface_ = eglCreateWindowSurface(display_, config, window_, nullptr);
    if (surface_ == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed");
        return false;
    }

    width_ = ANativeWindow_getWidth(window_);
    height_ = ANativeWindow_getHeight(window_);

    return true;
}

void EglContext::destroy() {
    if (display_ != EGL_NO_DISPLAY) {
        eglMakeCurrent(display_, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (surface_ != EGL_NO_SURFACE) {
            eglDestroySurface(display_, surface_);
        }
        if (context_ != EGL_NO_CONTEXT) {
            eglDestroyContext(display_, context_);
        }
        eglTerminate(display_);
    }
    display_ = EGL_NO_DISPLAY;
    surface_ = EGL_NO_SURFACE;
    context_ = EGL_NO_CONTEXT;

    if (window_ != nullptr) {
        ANativeWindow_release(window_);
        window_ = nullptr;
    }
}

bool EglContext::makeCurrent() const {
    return eglMakeCurrent(display_, surface_, surface_, context_) == EGL_TRUE;
}

bool EglContext::swapBuffers() const {
    return eglSwapBuffers(display_, surface_) == EGL_TRUE;
}

} // namespace gesture_canvas
