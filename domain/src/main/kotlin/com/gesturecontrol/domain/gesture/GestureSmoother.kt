package com.gesturecontrol.domain.gesture

class GestureSmoother(private val windowSize: Int = 5) {
    private val recentClasses = ArrayDeque<GestureClass>()

    fun smooth(gestureClass: GestureClass): GestureClass {
        recentClasses.addLast(gestureClass)
        if (recentClasses.size > windowSize) {
            recentClasses.removeFirst()
        }
        return recentClasses
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
    }
}
