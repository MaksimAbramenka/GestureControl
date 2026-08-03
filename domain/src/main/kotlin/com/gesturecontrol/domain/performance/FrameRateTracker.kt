package com.gesturecontrol.domain.performance

class FrameRateTracker(private val smoothingFactor: Float = 0.2f) {
    private var smoothedFps: Float? = null
    private var lastTimestampMs: Long? = null

    fun onFrame(timestampMs: Long): Float {
        val previousTimestampMs = lastTimestampMs
        if (previousTimestampMs == null) {
            lastTimestampMs = timestampMs
            return smoothedFps ?: 0f
        }

        val deltaMs = timestampMs - previousTimestampMs
        if (deltaMs <= 0) {
            return smoothedFps ?: 0f
        }
        lastTimestampMs = timestampMs

        val instantaneousFps = 1000f / deltaMs
        val previousSmoothedFps = smoothedFps
        smoothedFps = if (previousSmoothedFps == null) {
            instantaneousFps
        } else {
            previousSmoothedFps + smoothingFactor * (instantaneousFps - previousSmoothedFps)
        }
        return smoothedFps ?: 0f
    }
}
