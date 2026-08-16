package com.gesturecontrol.domain.voice

import com.gesturecontrol.domain.gesture.GestureClass

class PointHoldGate(private val holdThresholdMs: Long = 500L) {
    private var holdStartMs: Long? = null
    private var alreadyFired = false

    fun onFrame(gestureClass: GestureClass?, timestampMs: Long): Boolean {
        if (gestureClass != GestureClass.POINT) {
            holdStartMs = null
            alreadyFired = false
            return false
        }

        val startMs = holdStartMs ?: timestampMs.also { holdStartMs = it }
        if (!alreadyFired && timestampMs - startMs >= holdThresholdMs) {
            alreadyFired = true
            return true
        }
        return false
    }
}
