#include "ios-shim/EaglContext.h"

#import <OpenGLES/EAGL.h>
#import <QuartzCore/CAEAGLLayer.h>

#include "render/GLCompat.h"

namespace gesture_canvas {

EaglContext::~EaglContext() {
    if (context_ != nullptr) {
        CFBridgingRelease(context_);
        context_ = nullptr;
    }
}

bool EaglContext::init() {
    EAGLContext *context = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES3];
    if (context == nil) {
        return false;
    }
    context_ = const_cast<void *>(CFBridgingRetain(context));
    return true;
}

bool EaglContext::makeCurrent() const {
    if (context_ == nullptr) {
        return false;
    }
    EAGLContext *context = (__bridge EAGLContext *)context_;
    return [EAGLContext setCurrentContext:context] == YES;
}

bool EaglContext::bindDrawable(void *caLayer) const {
    if (context_ == nullptr || caLayer == nullptr) {
        return false;
    }
    EAGLContext *context = (__bridge EAGLContext *)context_;
    CAEAGLLayer *layer = (__bridge CAEAGLLayer *)caLayer;
    return [context renderbufferStorage:GL_RENDERBUFFER fromDrawable:layer] == YES;
}

bool EaglContext::presentRenderbuffer() const {
    if (context_ == nullptr) {
        return false;
    }
    EAGLContext *context = (__bridge EAGLContext *)context_;
    return [context presentRenderbuffer:GL_RENDERBUFFER] == YES;
}

}  // namespace gesture_canvas
