#pragma once

// Owns an EAGLContext -- the iOS counterpart to render/EglContext.h. Supports both offscreen use
// (mirroring the Android JNI bridge's nativeCaptureSnapshot path -- see Stage 3) and, via
// bindDrawable/presentRenderbuffer, an on-screen CAEAGLLayer-backed surface.

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

    // Allocates storage for the currently-bound GL_RENDERBUFFER from caLayer (an id<CAEAGLLayer>,
    // opaque here to keep this header Objective-C-free) -- the on-screen counterpart to
    // glRenderbufferStorage's explicit width/height for the offscreen case.
    bool bindDrawable(void *caLayer) const;

    // Presents the currently-bound GL_RENDERBUFFER to the screen.
    bool presentRenderbuffer() const;

private:
    void *context_ = nullptr;  // an EAGLContext*, opaque here to keep this header Objective-C-free
};

}  // namespace gesture_canvas
