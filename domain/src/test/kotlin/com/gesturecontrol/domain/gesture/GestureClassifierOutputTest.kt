package com.gesturecontrol.domain.gesture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GestureClassifierOutputTest {
    @Test
    fun `picks the class with the highest probability`() {
        // order matches GestureClass entries: IDLE, HOVER, DRAW, ERASE, POINT
        val probabilities = floatArrayOf(0.1f, 0.1f, 0.6f, 0.1f, 0.1f)

        val result = GestureClassifierOutput.interpret(probabilities)

        assertEquals(GestureClass.DRAW, result.gestureClass)
        assertEquals(0.6f, result.confidence)
    }

    @Test
    fun `picks IDLE when it has the highest probability`() {
        val probabilities = floatArrayOf(0.7f, 0.1f, 0.1f, 0.05f, 0.05f)

        val result = GestureClassifierOutput.interpret(probabilities)

        assertEquals(GestureClass.IDLE, result.gestureClass)
        assertEquals(0.7f, result.confidence)
    }

    @Test
    fun `picks ERASE when it has the highest probability`() {
        val probabilities = floatArrayOf(0.1f, 0.1f, 0.1f, 0.6f, 0.1f)

        val result = GestureClassifierOutput.interpret(probabilities)

        assertEquals(GestureClass.ERASE, result.gestureClass)
    }

    @Test
    fun `picks POINT when it has the highest probability`() {
        val probabilities = floatArrayOf(0.1f, 0.1f, 0.1f, 0.1f, 0.6f)

        val result = GestureClassifierOutput.interpret(probabilities)

        assertEquals(GestureClass.POINT, result.gestureClass)
    }

    @Test
    fun `throws when the probability count does not match the number of gesture classes`() {
        assertThrows<IllegalArgumentException> {
            GestureClassifierOutput.interpret(floatArrayOf(0.5f, 0.5f))
        }
    }
}
