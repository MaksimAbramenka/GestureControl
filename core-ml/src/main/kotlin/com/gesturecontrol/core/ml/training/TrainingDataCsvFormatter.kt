package com.gesturecontrol.core.ml.training

import com.gesturecontrol.domain.gesture.GestureClass

/** Pure CSV formatting for recorded training rows (label + normalized feature vector). */
object TrainingDataCsvFormatter {
    fun header(featureVectorSize: Int): String {
        val featureColumns = (0 until featureVectorSize).joinToString(",") { "f$it" }
        return "label,$featureColumns"
    }

    fun row(gestureClass: GestureClass, features: FloatArray): String {
        val featureValues = features.joinToString(",") { it.toString() }
        return "${gestureClass.name},$featureValues"
    }
}
