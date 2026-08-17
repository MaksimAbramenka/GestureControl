#pragma once

// Owns an offscreen EAGLContext -- the iOS counterpart to render/EglContext.h. No CAEAGLLayer is
// involved since this only needs to render into an off-screen framebuffer (mirroring the Android
// JNI bridge's nativeCaptureSnapshot path), not present a live on-screen surface.

namespace gesture_canvas {

class EaglContext {
public:
    EaglContext() = default;
    ~EaglContext();

    EaglContext(const EaglContext &) = delete;
    EaglContext &operator=(const EaglContext &) = delete;

    bool init();
    bool makeCurrent() const;
    bool isValid() const { return context_ != nullptr; }

private:
    void *context_ = nullptr;  // an EAGLContext*, opaque here to keep this header Objective-C-free
};

}  // namespace gesture_canvas
