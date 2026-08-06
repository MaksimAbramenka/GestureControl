#pragma once

#include <vector>

#include "scene/Stroke.h"

namespace gesture_canvas {

    struct RibbonVertex {
        float x;
        float y;
    };

// Converts a stroke's polyline + width into a triangle-strip vertex list (not GL_LINES, so
// strokes get proper width instead of hairline 1px segments).
// Pure geometry, no GL calls, testable in isolation.
    std::vector<RibbonVertex> tessellateRibbon(const std::vector<Point2D> &points, float width);

} // namespace gesture_canvas
