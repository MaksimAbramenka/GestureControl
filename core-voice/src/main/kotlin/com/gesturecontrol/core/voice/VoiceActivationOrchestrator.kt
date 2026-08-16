package com.gesturecontrol.core.voice

import kotlinx.coroutines.flow.firstOrNull

class VoiceActivationOrchestrator(
    private val speechRecognizerSource: SpeechRecognizerSource,
    private val voiceCommandClassifier: VoiceCommandClassifier,
) {
    suspend fun runOnce(): VoiceActivationResult {
        val event = speechRecognizerSource.listenOnce().firstOrNull() ?: return VoiceActivationResult.CaptureFailed
        return when (event) {
            is SpeechRecognitionEvent.Result -> VoiceActivationResult.Heard(
                transcript = event.transcript,
                command = voiceCommandClassifier.classify(event.transcript),
            )
            is SpeechRecognitionEvent.Error -> VoiceActivationResult.CaptureFailed
        }
    }
}
