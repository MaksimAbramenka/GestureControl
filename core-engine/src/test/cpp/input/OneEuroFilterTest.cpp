#include <gtest/gtest.h>

#include <cmath>

#include "input/OneEuroFilter.h"

using gesture_canvas::OneEuroFilter;

TEST(OneEuroFilterTest, FirstValuePassesThroughUnchanged) {
    OneEuroFilter filter;
    EXPECT_FLOAT_EQ(filter.filter(5.0f, 0.0f), 5.0f);
}

TEST(OneEuroFilterTest, ConstantSignalStaysConstant) {
    OneEuroFilter filter;
    filter.filter(3.0f, 0.0f);
    filter.filter(3.0f, 0.033f);
    float result = filter.filter(3.0f, 0.066f);
    EXPECT_NEAR(result, 3.0f, 1e-4f);
}

TEST(OneEuroFilterTest, SmoothsANoisySignalTowardItsCenter) {
    OneEuroFilter filter(/*minCutoff=*/1.0f, /*beta=*/0.0f, /*dCutoff=*/1.0f);
    float t = 0.0f;
    float last = 0.0f;
    for (int i = 0; i < 30; ++i) {
        float noisy = (i % 2 == 0) ? 1.1f : 0.9f; // oscillates around 1.0
        last = filter.filter(noisy, t);
        t += 0.033f;
    }
    // Steady-state output should settle much closer to the true center (1.0) than either raw
    // sample (0.9 / 1.1).
    EXPECT_NEAR(last, 1.0f, 0.05f);
}

TEST(OneEuroFilterTest, StepChangeIsNotAppliedInstantly) {
    OneEuroFilter filter;
    filter.filter(0.0f, 0.0f);
    float result = filter.filter(10.0f, 0.033f);
    EXPECT_GT(result, 0.0f);
    EXPECT_LT(result, 10.0f);
}

TEST(OneEuroFilterTest, HigherBetaRespondsFasterToFastMovement) {
    // beta controls how much the cutoff frequency (and thus responsiveness) increases with
    // signal velocity -- this adaptivity is the whole reason to use a 1-euro filter over plain
    // EMA for gesture input.
    OneEuroFilter lowBeta(/*minCutoff=*/1.0f, /*beta=*/0.0f, /*dCutoff=*/1.0f);
    OneEuroFilter highBeta(/*minCutoff=*/1.0f, /*beta=*/5.0f, /*dCutoff=*/1.0f);

    float lowResult = 0.0f;
    float highResult = 0.0f;
    float t = 0.0f;
    for (int i = 0; i <= 10; ++i) {
        float raw = static_cast<float>(i);
        lowResult = lowBeta.filter(raw, t);
        highResult = highBeta.filter(raw, t);
        t += 0.033f;
    }

    float target = 10.0f;
    EXPECT_LT(std::abs(highResult - target), std::abs(lowResult - target));
}

TEST(OneEuroFilterTest, ResetForgetsPreviousState) {
    OneEuroFilter filter;
    filter.filter(0.0f, 0.0f);
    filter.filter(10.0f, 0.033f);

    filter.reset();

    // After reset, the next value passes through unchanged, same as a fresh filter.
    EXPECT_FLOAT_EQ(filter.filter(50.0f, 1.0f), 50.0f);
}
