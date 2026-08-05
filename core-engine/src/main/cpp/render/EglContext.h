#pragma once

#include <android/native_window.h>
#include <EGL/egl.h>

namespace gesture_canvas {

// Owns the EGL display/context/surface bound to a native window. The native
// core drives its own EGL lifecycle rather than relying on GLSurfaceView's
// Java-side renderer thread, so the render entry point can be invoked
// explicitly from Kotlin (see NativeEngine.nativeRenderFrame).
class EglContext {
public:
    EglContext() = default;
    ~EglContext();

    EglContext(const EglContext &) = delete;
    EglContext &operator=(const EglContext &) = delete;

    // Initializes EGL against the given native window. Takes ownership of
    // `window` (released in the destructor or on the next init()).
    bool init(ANativeWindow *window);

    void destroy();

    bool makeCurrent() const;
    bool swapBuffers() const;

    int width() const { return width_; }
    int height() const { return height_; }
    bool isValid() const { return display_ != EGL_NO_DISPLAY && surface_ != EGL_NO_SURFACE; }

private:
    ANativeWindow *window_ = nullptr;
    EGLDisplay display_ = EGL_NO_DISPLAY;
    EGLSurface surface_ = EGL_NO_SURFACE;
    EGLContext context_ = EGL_NO_CONTEXT;
    int width_ = 0;
    int height_ = 0;
};

} // namespace gesture_canvas
