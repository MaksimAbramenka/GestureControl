#import "ios-shim/GCRenderView.h"

#import <QuartzCore/QuartzCore.h>

@implementation GCRenderView

+ (Class)layerClass {
    return [CAEAGLLayer class];
}

@end
