#include "input/PointSmoother.h"

namespace gesture_canvas {

Point2D PointSmoother::smooth(float x, float y, float timestampSeconds) {
    return {xFilter_.filter(x, timestampSeconds), yFilter_.filter(y, timestampSeconds)};
}

void PointSmoother::reset() {
    xFilter_.reset();
    yFilter_.reset();
}

} // namespace gesture_canvas
