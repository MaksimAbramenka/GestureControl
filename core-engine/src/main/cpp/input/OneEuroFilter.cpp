#include "input/OneEuroFilter.h"

#include <algorithm>
#include <cmath>

namespace gesture_canvas {

namespace {
constexpr float kPi = 3.14159265358979323846f;
constexpr float kMinDt = 1e-6f;
} // namespace

float OneEuroFilter::LowPass::apply(float input, float alpha) {
    if (!initialized) {
        initialized = true;
        value = input;
    } else {
        value = alpha * input + (1.0f - alpha) * value;
    }
    lastRaw = input;
    return value;
}

float OneEuroFilter::computeAlpha(float cutoff, float dt) {
    float tau = 1.0f / (2.0f * kPi * cutoff);
    return 1.0f / (1.0f + tau / dt);
}

OneEuroFilter::OneEuroFilter(float minCutoff, float beta, float dCutoff)
    : minCutoff_(minCutoff), beta_(beta), dCutoff_(dCutoff) {}

float OneEuroFilter::filter(float value, float timestampSeconds) {
    float dt = hasLastTime_ ? std::max(timestampSeconds - lastTime_, kMinDt) : 1.0f;
    lastTime_ = timestampSeconds;
    hasLastTime_ = true;

    float derivative = valueFilter_.initialized ? (value - valueFilter_.lastRaw) / dt : 0.0f;
    float smoothedDerivative = derivativeFilter_.apply(derivative, computeAlpha(dCutoff_, dt));

    float cutoff = minCutoff_ + beta_ * std::fabs(smoothedDerivative);
    return valueFilter_.apply(value, computeAlpha(cutoff, dt));
}

void OneEuroFilter::reset() {
    valueFilter_ = LowPass{};
    derivativeFilter_ = LowPass{};
    hasLastTime_ = false;
}

} // namespace gesture_canvas
