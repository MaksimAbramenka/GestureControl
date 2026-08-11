#include "render/RibbonTessellator.h"

#include <algorithm>
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

        Vec2 rotate(Vec2 v, float radians) {
            float c = std::cos(radians);
            float s = std::sin(radians);
            return {v.x * c - v.y * s, v.x * s + v.y * c};
        }

        float angleBetween(Vec2 from, Vec2 to) {
            float cross = from.x * to.y - from.y * to.x;
            float dot = from.x * to.x + from.y * to.y;
            return std::atan2(cross, dot);
        }

        constexpr float kReversalDotThreshold = -0.5f;
        constexpr int kRoundJoinSteps = 3;

        constexpr int kSamplesPerSegment = 20;

        const Point2D &clampedAt(const std::vector<Point2D> &points, int index) {
            int clamped = std::max(0, std::min(static_cast<int>(points.size()) - 1, index));
            return points[static_cast<size_t>(clamped)];
        }

    } // namespace

    std::vector<Point2D> smoothPath(const std::vector<Point2D> &points) {
        if (points.size() < 3) {
            return points;
        }

        std::vector<Point2D> result;
        result.reserve(points.size() * kSamplesPerSegment);

        for (size_t i = 0; i + 1 < points.size(); ++i) {
            const Point2D &p0 = clampedAt(points, static_cast<int>(i) - 1);
            const Point2D &p1 = points[i];
            const Point2D &p2 = points[i + 1];
            const Point2D &p3 = clampedAt(points, static_cast<int>(i) + 2);

            // Segment 0 emits its t=0 sample too (the very first point); every other segment
            // picks up at t>0 since the previous segment already emitted this shared endpoint.
            int startStep = (i == 0) ? 0 : 1;
            for (int step = startStep; step <= kSamplesPerSegment; ++step) {
                float t = static_cast<float>(step) / static_cast<float>(kSamplesPerSegment);
                float t2 = t * t;
                float t3 = t2 * t;

                float x = 0.5f * ((2.0f * p1.x) + (-p0.x + p2.x) * t +
                                  (2.0f * p0.x - 5.0f * p1.x + 4.0f * p2.x - p3.x) * t2 +
                                  (-p0.x + 3.0f * p1.x - 3.0f * p2.x + p3.x) * t3);
                float y = 0.5f * ((2.0f * p1.y) + (-p0.y + p2.y) * t +
                                  (2.0f * p0.y - 5.0f * p1.y + 4.0f * p2.y - p3.y) * t2 +
                                  (-p0.y + 3.0f * p1.y - 3.0f * p2.y + p3.y) * t3);
                result.push_back({x, y});
            }
        }

        return result;
    }

    std::vector<RibbonVertex> tessellateRibbon(const std::vector<Point2D> &rawPoints, float width) {
        std::vector<RibbonVertex> vertices;
        if (rawPoints.size() < 2) {
            return vertices;
        }

        std::vector<Point2D> points = smoothPath(rawPoints);

        const float halfWidth = width * 0.5f;
        vertices.reserve(points.size() * 2);

        auto emitVertexPair = [&](size_t pointIndex, Vec2 normal) {
            vertices.push_back({points[pointIndex].x + normal.x * halfWidth,
                                points[pointIndex].y + normal.y * halfWidth});
            vertices.push_back({points[pointIndex].x - normal.x * halfWidth,
                                points[pointIndex].y - normal.y * halfWidth});
        };

        for (size_t i = 0; i < points.size(); ++i) {
            if (i == 0 || i == points.size() - 1) {
                Vec2 direction = i == 0
                                 ? Vec2{points[1].x - points[0].x, points[1].y - points[0].y}
                                 : Vec2{points[i].x - points[i - 1].x,
                                        points[i].y - points[i - 1].y};
                emitVertexPair(i, perpendicular(normalize(direction)));
                continue;
            }

            Vec2 previousDir = normalize(
                    {points[i].x - points[i - 1].x, points[i].y - points[i - 1].y});
            Vec2 nextDir = normalize(
                    {points[i + 1].x - points[i].x, points[i + 1].y - points[i].y});
            const float dot = previousDir.x * nextDir.x + previousDir.y * nextDir.y;

            if (dot > kReversalDotThreshold) {
                Vec2 blended = {points[i + 1].x - points[i - 1].x,
                                points[i + 1].y - points[i - 1].y};
                emitVertexPair(i, perpendicular(normalize(blended)));
            } else {
                Vec2 previousNormal = perpendicular(previousDir);
                Vec2 nextNormal = perpendicular(nextDir);
                const float sweep = angleBetween(previousNormal, nextNormal);
                for (int step = 0; step <= kRoundJoinSteps; ++step) {
                    const float t = static_cast<float>(step) / static_cast<float>(kRoundJoinSteps);
                    emitVertexPair(i, rotate(previousNormal, sweep * t));
                }
            }
        }

        return vertices;
    }

} // namespace gesture_canvas
