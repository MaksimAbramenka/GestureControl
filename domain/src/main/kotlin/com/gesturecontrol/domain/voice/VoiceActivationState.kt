package com.gesturecontrol.domain.voice

sealed class VoiceActivationState {
    object Idle : VoiceActivationState()
    data class SingleShotListening(val previousState: VoiceActivationState) : VoiceActivationState()
    object ContinuousListening : VoiceActivationState()
}
