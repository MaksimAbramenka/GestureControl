#include "render/RibbonTessellator.h"

#include <cmath>

namespace gesture_canvas {

    namespace {

        struct Vec2 {
            float x;
            float y;
        };

        Vec2 normalize(Vec2 v) {
            float length = std::sqrt(v.x * v.x + v.y * v.y);
            if (length < 1e-6f) {
                return {0.0f, 0.0f};
            }
            return {v.x / length, v.y / length};
        }

        Vec2 perpendicular(Vec2 direction) {
            return {-direction.y, direction.x};
        }

    } // namespace

    std::vector<RibbonVertex> tessellateRibbon(const std::vector<Point2D> &points, float width) {
        std::vector<RibbonVertex> vertices;
        if (points.size() < 2) {
            return vertices;
        }

        const float halfWidth = width * 0.5f;
        vertices.reserve(points.size() * 2);

        for (size_t i = 0; i < points.size(); ++i) {
            Vec2 direction;
            if (i == 0) {
                direction = {points[1].x - points[0].x, points[1].y - points[0].y};
            } else if (i == points.size() - 1) {
                direction = {points[i].x - points[i - 1].x, points[i].y - points[i - 1].y};
            } else {
                direction = {points[i + 1].x - points[i - 1].x, points[i + 1].y - points[i - 1].y};
            }

            Vec2 normal = perpendicular(normalize(direction));

            vertices.push_back(
                    {points[i].x + normal.x * halfWidth, points[i].y + normal.y * halfWidth});
            vertices.push_back(
                    {points[i].x - normal.x * halfWidth, points[i].y - normal.y * halfWidth});
        }

        return vertices;
    }

} // namespace gesture_canvas
