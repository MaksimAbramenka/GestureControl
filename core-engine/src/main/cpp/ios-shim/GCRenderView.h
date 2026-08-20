#import <UIKit/UIKit.h>

// A UIView backed by a CAEAGLLayer instead of the default CALayer -- the on-screen surface
// gc_renderer_init_onscreen binds an EAGLContext's renderbuffer storage to. Overriding
// +layerClass is a class-level (metaclass) method, which Kotlin/Native's Objective-C interop
// can't express when subclassing an Objective-C class -- trivial in Objective-C++, so this one
// small view class stays here rather than being authored in Kotlin like the rest of the iOS-side
// rendering code.
@interface GCRenderView : UIView
@end
