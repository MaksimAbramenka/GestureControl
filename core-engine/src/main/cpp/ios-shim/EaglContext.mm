#include "ios-shim/EaglContext.h"

#import <OpenGLES/EAGL.h>

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

}  // namespace gesture_canvas
