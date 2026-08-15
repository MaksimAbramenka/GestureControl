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
    fun `a hold trigger from Idle enters single-shot listening, remembering Idle as the return state`() {
        val controller = VoiceActivationController()

        controller.onPointHoldTriggered()

        assertEquals(VoiceActivationState.SingleShotListening(VoiceActivationState.Idle), controller.state)
    }

    @Test
    fun `a hold trigger from continuous listening enters single-shot, remembering continuous as the return state`() {
        val controller = VoiceActivationController()
        controller.onCommandCaptured(Command.StartContinuousListening)

        controller.onPointHoldTriggered()

        assertEquals(
            VoiceActivationState.SingleShotListening(VoiceActivationState.ContinuousListening),
            controller.state,
        )
    }

    @Test
    fun `a hold trigger while already single-shot listening is a no-op`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()
        val stateAfterFirstTrigger = controller.state

        controller.onPointHoldTriggered()

        assertEquals(stateAfterFirstTrigger, controller.state)
    }

    @Test
    fun `capturing a normal command from single-shot listening returns to Idle`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onCommandCaptured(Command.Undo)

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `capturing a normal command from single-shot returns to continuous listening if that was the previous state`() {
        val controller = VoiceActivationController()
        controller.onCommandCaptured(Command.StartContinuousListening)
        controller.onPointHoldTriggered()

        controller.onCommandCaptured(Command.SetBrushColor(BrushColorName.RED))

        assertEquals(VoiceActivationState.ContinuousListening, controller.state)
    }

    @Test
    fun `capturing Unrecognized from single-shot listening returns to the previous state`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onCommandCaptured(Command.Unrecognized)

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `a listening timeout from single-shot listening returns to the previous state`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onListeningTimeout()

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `capturing StartContinuousListening enters continuous listening from single-shot`() {
        val controller = VoiceActivationController()
        controller.onPointHoldTriggered()

        controller.onCommandCaptured(Command.StartContinuousListening)

        assertEquals(VoiceActivationState.ContinuousListening, controller.state)
    }

    @Test
    fun `capturing StopContinuousListening returns to Idle from continuous listening`() {
        val controller = VoiceActivationController()
        controller.onCommandCaptured(Command.StartContinuousListening)

        controller.onCommandCaptured(Command.StopContinuousListening)

        assertEquals(VoiceActivationState.Idle, controller.state)
    }

    @Test
    fun `capturing StopContinuousListening from single-shot listening goes straight to Idle, not back to continuous`() {
        val controller = VoiceActivationController()
        controller.onCommandCaptured(Command.StartContinuousListening)
        controller.onPointHoldTriggered()

        controller.onCommandCaptured(Command.StopContinuousListening)

        assertEquals(VoiceActivationState.Idle, controller.state)
    }
}
