package com.gesturecontrol.domain.training

import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.hand.Handedness
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecordingProgressTest {
    @Test
    fun `an unrecorded combination starts at zero`() {
        val progress = RecordingProgress()

        assertEquals(0, progress.count(GestureClass.DRAW, Handedness.LEFT))
    }

    @Test
    fun `increment adds one to only the matching combination`() {
        val progress = RecordingProgress()
            .increment(GestureClass.DRAW, Handedness.LEFT)
            .increment(GestureClass.DRAW, Handedness.LEFT)

        assertEquals(2, progress.count(GestureClass.DRAW, Handedness.LEFT))
        assertEquals(0, progress.count(GestureClass.DRAW, Handedness.RIGHT))
        assertEquals(0, progress.count(GestureClass.HOVER, Handedness.LEFT))
    }

    @Test
    fun `withCount sets an absolute value, used when loading persisted state`() {
        val progress = RecordingProgress().withCount(GestureClass.ERASE, Handedness.RIGHT, 42)

        assertEquals(42, progress.count(GestureClass.ERASE, Handedness.RIGHT))
    }

    @Test
    fun `isComplete is false unless every gesture-hand combination meets the threshold`() {
        var progress = RecordingProgress()
        for (gestureClass in GestureClass.entries) {
            for (handedness in Handedness.entries) {
                if (gestureClass == GestureClass.IDLE && handedness == Handedness.RIGHT) continue
                progress = progress.withCount(gestureClass, handedness, 100)
            }
        }

        assertFalse(progress.isComplete(threshold = 100))
    }

    @Test
    fun `isComplete is true once every gesture-hand combination meets the threshold`() {
        var progress = RecordingProgress()
        for (gestureClass in GestureClass.entries) {
            for (handedness in Handedness.entries) {
                progress = progress.withCount(gestureClass, handedness, 100)
            }
        }

        assertTrue(progress.isComplete(threshold = 100))
    }
}
