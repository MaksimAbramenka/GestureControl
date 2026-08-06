#include <gtest/gtest.h>

#include <cmath>

#include "render/RibbonTessellator.h"

using gesture_canvas::Point2D;
using gesture_canvas::tessellateRibbon;

TEST(RibbonTessellatorTest, FewerThanTwoPointsProducesNoVertices) {
    EXPECT_TRUE(tessellateRibbon({}, 0.1f).empty());
    EXPECT_TRUE(tessellateRibbon({{0.0f, 0.0f}}, 0.1f).empty());
}

TEST(RibbonTessellatorTest, ProducesTwoVerticesPerPoint) {
    std::vector<Point2D> points = {{0.0f, 0.0f}, {1.0f, 0.0f}, {2.0f, 0.0f}};

    auto vertices = tessellateRibbon(points, 0.2f);

    EXPECT_EQ(vertices.size(), points.size() * 2);
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
