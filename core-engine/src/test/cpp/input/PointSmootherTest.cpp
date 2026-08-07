#include <gtest/gtest.h>

#include "input/PointSmoother.h"

using gesture_canvas::PointSmoother;

TEST(PointSmootherTest, FirstPointPassesThroughUnchanged) {
    PointSmoother smoother;
    auto point = smoother.smooth(0.2f, 0.4f, 0.0f);
    EXPECT_FLOAT_EQ(point.x, 0.2f);
    EXPECT_FLOAT_EQ(point.y, 0.4f);
}

TEST(PointSmootherTest, ResetTreatsTheNextPointAsFirst) {
    PointSmoother smoother;
    smoother.smooth(0.0f, 0.0f, 0.0f);
    smoother.smooth(1.0f, 1.0f, 0.033f);

    smoother.reset();

    auto point = smoother.smooth(0.5f, 0.7f, 1.0f);
    EXPECT_FLOAT_EQ(point.x, 0.5f);
    EXPECT_FLOAT_EQ(point.y, 0.7f);
}
