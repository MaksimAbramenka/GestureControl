package com.gesturecontrol.core.voice

import com.gesturecontrol.domain.voice.Command

sealed class VoiceActivationResult {
    data class Heard(val transcript: String, val command: Command) : VoiceActivationResult()
    data object CaptureFailed : VoiceActivationResult()
}
