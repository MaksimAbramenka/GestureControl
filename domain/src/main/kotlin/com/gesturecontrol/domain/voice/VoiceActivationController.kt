package com.gesturecontrol.domain.voice

class VoiceActivationController {
    var state: VoiceActivationState = VoiceActivationState.Idle
        private set

    fun onPointHoldTriggered() {
        if (state is VoiceActivationState.SingleShotListening) return
        state = VoiceActivationState.SingleShotListening(previousState = state)
    }

    fun onCommandCaptured(command: Command) {
        when (command) {
            is Command.StartContinuousListening -> state = VoiceActivationState.ContinuousListening
            is Command.StopContinuousListening -> state = VoiceActivationState.Idle
            else -> returnFromSingleShot()
        }
    }

    fun onListeningTimeout() {
        returnFromSingleShot()
    }

    private fun returnFromSingleShot() {
        val current = state
        if (current is VoiceActivationState.SingleShotListening) {
            state = current.previousState
        }
    }
}
