package com.gesturecontrol.domain.hand

/**
 * Exponential moving average smoothing for a displayed cursor position. This is display-only
 * smoothing for UI feedback (e.g. a gesture indicator) -- the actual drawing input already gets
 * its own native 1€ filter, so this exists purely to take the jitter out of what's on screen.
 */
class PositionSmoother(private val smoothingFactor: Float = 0.5f) {
    private var smoothed: NormalizedPoint? = null

    fun smooth(point: NormalizedPoint?): NormalizedPoint? {
        if (point == null) {
            smoothed = null
            return null
        }

        val previous = smoothed
        val result = if (previous == null) {
            point
        } else {
            NormalizedPoint(
                x = previous.x + smoothingFactor * (point.x - previous.x),
                y = previous.y + smoothingFactor * (point.y - previous.y),
                z = point.z,
            )
        }
        smoothed = result
        return result
    }
}
