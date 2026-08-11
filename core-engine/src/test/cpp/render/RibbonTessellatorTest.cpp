#include <gtest/gtest.h>

#include <cmath>

#include "render/RibbonTessellator.h"

using gesture_canvas::Point2D;
using gesture_canvas::smoothPath;
using gesture_canvas::tessellateRibbon;

namespace {

    float distance(const Point2D &a, const Point2D &b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return std::sqrt(dx * dx + dy * dy);
    }

} // namespace

// ---- smoothPath -----------------------------------------------------------------------------
// Raw hand-tracked points are spaced however fast the hand was moving -- fast strokes leave
// sharp-angled gaps between samples that no amount of tessellator join logic can fully hide.
// smoothPath traces a Catmull-Rom spline through them first, so the tessellator downstream sees a
// densely-sampled, naturally smooth curve instead of a sparse polyline.

TEST(SmoothPathTest, FewerThanThreePointsReturnsInputUnchanged) {
    std::vector<Point2D> empty;
    std::vector<Point2D> one = {{1.0f, 2.0f}};
    std::vector<Point2D> two = {{0.0f, 0.0f}, {1.0f, 1.0f}};

    EXPECT_TRUE(smoothPath(empty).empty());

    auto smoothedOne = smoothPath(one);
    ASSERT_EQ(smoothedOne.size(), 1u);
    EXPECT_NEAR(smoothedOne[0].x, one[0].x, 1e-5f);
    EXPECT_NEAR(smoothedOne[0].y, one[0].y, 1e-5f);

    auto smoothedTwo = smoothPath(two);
    ASSERT_EQ(smoothedTwo.size(), 2u);
    EXPECT_NEAR(smoothedTwo[0].x, two[0].x, 1e-5f);
    EXPECT_NEAR(smoothedTwo[1].x, two[1].x, 1e-5f);
}

TEST(SmoothPathTest, ProducesManyMoreSamplesThanInputPoints) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.5f}, {2.0f, 0.0f}, {3.0f, 0.5f}};

    auto smoothed = smoothPath(points);

    EXPECT_GT(smoothed.size(), points.size() * 4);
}

TEST(SmoothPathTest, StartsExactlyAtTheFirstInputPoint) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.5f}, {2.0f, 0.0f}};

    auto smoothed = smoothPath(points);

    ASSERT_FALSE(smoothed.empty());
    EXPECT_NEAR(smoothed.front().x, points.front().x, 1e-4f);
    EXPECT_NEAR(smoothed.front().y, points.front().y, 1e-4f);
}

TEST(SmoothPathTest, EndsExactlyAtTheLastInputPoint) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.5f}, {2.0f, 0.0f}};

    auto smoothed = smoothPath(points);

    ASSERT_FALSE(smoothed.empty());
    EXPECT_NEAR(smoothed.back().x, points.back().x, 1e-4f);
    EXPECT_NEAR(smoothed.back().y, points.back().y, 1e-4f);
}

TEST(SmoothPathTest, PassesThroughEveryOriginalInteriorPoint) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 2.0f}, {2.0f, -1.0f}, {3.0f, 0.0f}};

    auto smoothed = smoothPath(points);

    for (const auto &original: points) {
        float closest = 1e9f;
        for (const auto &sample: smoothed) {
            closest = std::min(closest, distance(original, sample));
        }
        EXPECT_LT(closest, 1e-3f);
    }
}

TEST(SmoothPathTest, CollinearPointsStayExactlyCollinear) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.0f}, {2.0f, 0.0f}, {3.0f, 0.0f}};

    auto smoothed = smoothPath(points);

    for (const auto &sample: smoothed) {
        EXPECT_NEAR(sample.y, 0.0f, 1e-4f);
    }
}

// ---- tessellateRibbon -------------------------------------------------------------------------

TEST(RibbonTessellatorTest, FewerThanTwoPointsProducesNoVertices) {
    EXPECT_TRUE(tessellateRibbon({}, 0.1f).empty());
    EXPECT_TRUE(tessellateRibbon({{0.0f, 0.0f}}, 0.1f).empty());
}

TEST(RibbonTessellatorTest, HorizontalSegmentOffsetsVerticallyByHalfWidth) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.0f}};

    auto vertices = tessellateRibbon(points, 0.4f);

    ASSERT_EQ(vertices.size(), 4u);
    EXPECT_NEAR(vertices[0].x, 0.0f, 1e-5f);
    EXPECT_NEAR(std::abs(vertices[0].y), 0.2f, 1e-5f);
    EXPECT_NEAR(vertices[0].y, -vertices[1].y, 1e-5f);
}

TEST(RibbonTessellatorTest, VerticalSegmentOffsetsHorizontallyByHalfWidth) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {0.0f, 1.0f}};

    auto vertices = tessellateRibbon(points, 0.4f);

    ASSERT_EQ(vertices.size(), 4u);
    EXPECT_NEAR(std::abs(vertices[0].x), 0.2f, 1e-5f);
    EXPECT_NEAR(vertices[0].y, 0.0f, 1e-5f);
}

TEST(RibbonTessellatorTest, StraightMultiPointLineStaysStraightAfterSmoothing) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.0f}, {2.0f, 0.0f}};

    auto vertices = tessellateRibbon(points, 0.2f);

    ASSERT_FALSE(vertices.empty());
    for (const auto &vertex: vertices) {
        EXPECT_NEAR(std::abs(vertex.y), 0.1f, 1e-3f);
    }
}

// A sharp reversal (e.g. the tip of a tight loop in a fast freehand stroke) is where the old
// approach broke down, whether via a single averaged normal (collapses to zero width) or a
// join-angle threshold tuned for it (facets ordinary curves instead). Smoothing the path first
// turns the corner into a dense curve before any join logic even runs.
TEST(RibbonTessellatorTest, SharpReversalProducesNoDegenerateVertices) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}};

    auto vertices = tessellateRibbon(points, 0.4f);

    ASSERT_FALSE(vertices.empty());
    for (const auto &vertex: vertices) {
        EXPECT_FALSE(std::isnan(vertex.x));
        EXPECT_FALSE(std::isnan(vertex.y));
    }
    EXPECT_GT(vertices.size(), points.size() * 2);
}

TEST(RibbonTessellatorTest, FastSparseCurveProducesNoDegenerateVertices) {
    // Simulates a quickly-drawn curve: few points, each a large step apart, with a real direction
    // change -- the exact shape that exposed both the original gap bug and the later faceting
    // regression.
    std::vector<Point2D> points = {
            {0.0f,  0.0f},
            {2.0f,  0.5f},
            {3.0f,  3.0f},
            {1.5f,  4.0f},
            {-1.0f, 2.5f},
    };

    auto vertices = tessellateRibbon(points, 0.3f);

    ASSERT_FALSE(vertices.empty());
    for (const auto &vertex: vertices) {
        EXPECT_FALSE(std::isnan(vertex.x));
        EXPECT_FALSE(std::isnan(vertex.y));
    }
}
