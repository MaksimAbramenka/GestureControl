package com.gesturecontrol.core.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.io.Closeable

private const val TAG = "VoiceSTT"

sealed class SpeechRecognitionEvent {
    data class Result(val transcript: String) : SpeechRecognitionEvent()
    data class Error(val code: Int) : SpeechRecognitionEvent()
}

class SpeechRecognizerSource(private val context: Context) : Closeable {
    private val recognizer: SpeechRecognizer? by lazy {
        if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            null
        }
    }

    fun listenOnce(): Flow<SpeechRecognitionEvent> = callbackFlow {
        val currentRecognizer = recognizer
        if (currentRecognizer == null) {
            trySend(SpeechRecognitionEvent.Error(SpeechRecognizer.ERROR_CLIENT))
            close()
            return@callbackFlow
        }

        currentRecognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    Log.d(TAG, "onError code=$error")
                    trySend(SpeechRecognitionEvent.Error(error))
                    close()
                }

                override fun onResults(results: Bundle) {
                    val transcript = results
                        .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    Log.d(TAG, "onResults transcript='$transcript'")
                    trySend(SpeechRecognitionEvent.Result(transcript))
                    close()
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            },
        )

        currentRecognizer.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            },
        )

        awaitClose { currentRecognizer.stopListening() }
    }

    override fun close() {
        recognizer?.destroy()
    }
}
