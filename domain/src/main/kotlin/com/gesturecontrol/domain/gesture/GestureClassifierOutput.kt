package com.gesturecontrol.domain.gesture

data class ClassifiedGesture(val gestureClass: GestureClass, val confidence: Float)

object GestureClassifierOutput {
    fun interpret(probabilities: FloatArray): ClassifiedGesture {
        require(probabilities.size == GestureClass.entries.size) {
            "Expected ${GestureClass.entries.size} probabilities (one per GestureClass), got ${probabilities.size}"
        }

        val maxIndex = probabilities.indices.maxBy { probabilities[it] }
        return ClassifiedGesture(GestureClass.entries[maxIndex], probabilities[maxIndex])
    }
}
