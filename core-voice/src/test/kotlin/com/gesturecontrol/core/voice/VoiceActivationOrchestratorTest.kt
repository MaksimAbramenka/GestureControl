package com.gesturecontrol.core.voice

import android.speech.SpeechRecognizer
import com.gesturecontrol.domain.voice.Command
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VoiceActivationOrchestratorTest {
    private val speechRecognizerSource = mockk<SpeechRecognizerSource>()
    private val voiceCommandClassifier = mockk<VoiceCommandClassifier>()
    private val orchestrator = VoiceActivationOrchestrator(speechRecognizerSource, voiceCommandClassifier)

    @Test
    fun `returns Heard with the transcript and classified command when speech is recognized`() = runTest {
        every { speechRecognizerSource.listenOnce() } returns
            flowOf(SpeechRecognitionEvent.Result("clear the canvas"))
        coEvery { voiceCommandClassifier.classify("clear the canvas") } returns Command.Clear

        val result = orchestrator.runOnce()

        assertEquals(VoiceActivationResult.Heard("clear the canvas", Command.Clear), result)
    }

    @Test
    fun `returns Heard with Unrecognized when the classifier does not recognize the transcript`() = runTest {
        every { speechRecognizerSource.listenOnce() } returns
            flowOf(SpeechRecognitionEvent.Result("what's the weather"))
        coEvery { voiceCommandClassifier.classify("what's the weather") } returns Command.Unrecognized

        val result = orchestrator.runOnce()

        assertEquals(VoiceActivationResult.Heard("what's the weather", Command.Unrecognized), result)
    }

    @Test
    fun `returns CaptureFailed when speech recognition errors`() = runTest {
        every { speechRecognizerSource.listenOnce() } returns
            flowOf(SpeechRecognitionEvent.Error(SpeechRecognizer.ERROR_NO_MATCH))

        val result = orchestrator.runOnce()

        assertEquals(VoiceActivationResult.CaptureFailed, result)
    }

    @Test
    fun `returns CaptureFailed when the recognizer emits nothing at all`() = runTest {
        every { speechRecognizerSource.listenOnce() } returns emptyFlow()

        val result = orchestrator.runOnce()

        assertEquals(VoiceActivationResult.CaptureFailed, result)
    }

    @Test
    fun `never calls classify when speech recognition failed`() = runTest {
        every { speechRecognizerSource.listenOnce() } returns
            flowOf(SpeechRecognitionEvent.Error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))

        orchestrator.runOnce()

        coVerify(exactly = 0) { voiceCommandClassifier.classify(any()) }
    }
}
