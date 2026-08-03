package com.gesturecontrol.core.ml.training

import com.gesturecontrol.domain.gesture.GestureClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrainingDataCsvFormatterTest {
    @Test
    fun `header has one label column and one column per feature`() {
        val header = TrainingDataCsvFormatter.header(featureVectorSize = 4)

        assertEquals("label,f0,f1,f2,f3", header)
    }

    @Test
    fun `row starts with the class name followed by each feature value`() {
        val row = TrainingDataCsvFormatter.row(
            gestureClass = GestureClass.DRAW,
            features = floatArrayOf(0.1f, -0.5f, 1.0f),
        )

        assertEquals("DRAW,0.1,-0.5,1.0", row)
    }

    @Test
    fun `row and header have the same number of columns for the same feature vector size`() {
        val features = FloatArray(63) { it / 100f }

        val header = TrainingDataCsvFormatter.header(featureVectorSize = features.size)
        val row = TrainingDataCsvFormatter.row(gestureClass = GestureClass.IDLE, features = features)

        assertEquals(header.split(",").size, row.split(",").size)
    }
}
