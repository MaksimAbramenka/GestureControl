package com.gesturecontrol.domain.training

import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.hand.Handedness

data class RecordingProgress(private val counts: Map<Pair<GestureClass, Handedness>, Int> = emptyMap()) {
    fun count(
        gestureClass: GestureClass,
        handedness: Handedness,
    ): Int = counts[gestureClass to handedness] ?: 0

    fun increment(
        gestureClass: GestureClass,
        handedness: Handedness,
    ): RecordingProgress = withCount(gestureClass, handedness, count(gestureClass, handedness) + 1)

    fun withCount(
        gestureClass: GestureClass,
        handedness: Handedness,
        count: Int,
    ): RecordingProgress = copy(counts = counts + ((gestureClass to handedness) to count))

    fun isComplete(threshold: Int): Boolean =
        GestureClass.entries.all { gestureClass ->
            Handedness.entries.all { handedness -> count(gestureClass, handedness) >= threshold }
        }
}
