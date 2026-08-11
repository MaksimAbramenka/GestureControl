#pragma once

#include <vector>

#include "scene/Stroke.h"

namespace gesture_canvas {

    struct RibbonVertex {
        float x;
        float y;
    };

// Traces a smooth Catmull-Rom spline through a polyline, inserting interpolated points along the
// way. Raw points are hand-tracking samples spaced however fast the hand was moving, so a fast
// stroke leaves sharp-angled gaps between them; this turns that sparse polyline into a densely
// sampled curve before it reaches the ribbon tessellator, which is what actually keeps fast
// strokes looking smooth rather than faceted at every direction change.
// Pure geometry, testable in isolation.
    std::vector<Point2D> smoothPath(const std::vector<Point2D> &points);

// Converts a stroke's polyline + width into a triangle-strip vertex list (not GL_LINES, so
// strokes get proper width instead of hairline 1px segments). Smooths the path first (see
// smoothPath), so the join geometry only has to handle genuinely pathological cases (e.g.
// duplicate points), not ordinary fast-drawn curves.
// Pure geometry, no GL calls, testable in isolation.
    std::vector<RibbonVertex> tessellateRibbon(const std::vector<Point2D> &points, float width);

} // namespace gesture_canvas
