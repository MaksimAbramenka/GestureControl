package com.gesturecontrol.domain.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VoiceActivationControllerTest {
    @Test
    fun `starts in Idle`() {
        val controller = VoiceActivationController()

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `a hold trigger from Idle enters single-shot listening`() {
        val controller = VoiceActivationController()

        controller.onPointHoldTriggered()

        assertEquals(VoiceActivationState.SingleShotListening, controller.state)
    }

    @Test
    fun `a hold trigger while already single-shot listening is a no-op`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onPointHoldTriggered()

        assertEquals(VoiceActivationState.SingleShotListening, controller.state)
    }

    @Test
    fun `capturing a command from single-shot listening returns to Idle`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onCommandCaptured()

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `a listening timeout from single-shot listening returns to Idle`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onListeningTimeout()

        assertEquals(VoiceActivationState.Idle, controller.state)
    }
}
