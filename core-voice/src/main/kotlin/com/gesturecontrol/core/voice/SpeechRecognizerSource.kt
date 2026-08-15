package com.gesturecontrol.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed class SpeechRecognitionEvent {
    data class Result(val transcript: String) : SpeechRecognitionEvent()
    data class Error(val code: Int) : SpeechRecognitionEvent()
}

class SpeechRecognizerSource(private val context: Context) {
    fun listenOnce(): Flow<SpeechRecognitionEvent> = callbackFlow {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            trySend(SpeechRecognitionEvent.Error(SpeechRecognizer.ERROR_CLIENT))
            close()
            return@callbackFlow
        }

        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    trySend(SpeechRecognitionEvent.Error(error))
                    close()
                }

                override fun onResults(results: Bundle) {
                    val transcript = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    trySend(SpeechRecognitionEvent.Result(transcript))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        recognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            },
        )

        awaitClose {
            recognizer.stopListening()
            recognizer.destroy()
        }
    }
}
