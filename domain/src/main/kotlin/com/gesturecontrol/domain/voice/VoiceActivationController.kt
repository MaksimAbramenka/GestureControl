package com.gesturecontrol.domain.voice

class VoiceActivationController {
    var state: VoiceActivationState = VoiceActivationState.Idle
        private set

    fun onPointHoldTriggered() {
        if (state is VoiceActivationState.SingleShotListening) return
        state = VoiceActivationState.SingleShotListening
    }

    fun onCommandCaptured() {
        state = VoiceActivationState.Idle
    }

    fun onListeningTimeout() {
        state = VoiceActivationState.Idle
    }
}
