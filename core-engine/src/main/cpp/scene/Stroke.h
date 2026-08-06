#pragma once

#include <vector>

namespace gesture_canvas {

    struct Point2D {
        float x;
        float y;
    };

    struct Stroke {
        std::vector<Point2D> points;
        // Bright cyan by default -- stays visible against most camera backgrounds (skin, indoor
        // scenes) unlike white, which can vanish against a bright feed.
        float r = 0.1f;
        float g = 0.9f;
        float b = 1.0f;
        float width = 0.015f;
    };

} // namespace gesture_canvas
