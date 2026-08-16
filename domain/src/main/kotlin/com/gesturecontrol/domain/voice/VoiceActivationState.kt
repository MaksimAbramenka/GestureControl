package com.gesturecontrol.domain.voice

sealed class VoiceActivationState {
    object Idle : VoiceActivationState()
    object SingleShotListening : VoiceActivationState()
}
