#pragma once

#include "input/OneEuroFilter.h"
#include "scene/Stroke.h"

namespace gesture_canvas {

// Smooths a stream of 2D points (independent 1-euro filters per axis) before they're appended
// to a stroke -- gesture-tracked coordinates are noisier than touch input. Call reset() at the
// start of a new stroke so it doesn't inherit stale filter state from the previous one.
class PointSmoother {
public:
    Point2D smooth(float x, float y, float timestampSeconds);
    void reset();

private:
    OneEuroFilter xFilter_;
    OneEuroFilter yFilter_;
};

} // namespace gesture_canvas
