#pragma once

namespace gesture_canvas {

// Adaptive low-pass filter (Casiez et al., "1-euro filter") -- smooths a noisy signal while
// staying responsive to fast changes, by raising the cutoff frequency (less smoothing) as the
// estimated signal velocity grows. Suited to gesture-tracked input, which is noisier than touch
// but should still feel responsive during a fast stroke.
class OneEuroFilter {
public:
    explicit OneEuroFilter(float minCutoff = 1.0f, float beta = 0.0f, float dCutoff = 1.0f);

    // timestampSeconds must be monotonically non-decreasing across calls.
    float filter(float value, float timestampSeconds);

    void reset();

private:
    struct LowPass {
        bool initialized = false;
        float lastRaw = 0.0f;
        float value = 0.0f;

        float apply(float input, float alpha);
    };

    static float computeAlpha(float cutoff, float dt);

    float minCutoff_;
    float beta_;
    float dCutoff_;
    LowPass valueFilter_;
    LowPass derivativeFilter_;
    float lastTime_ = 0.0f;
    bool hasLastTime_ = false;
};

} // namespace gesture_canvas
