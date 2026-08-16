package com.gesturecontrol.core.voice

import android.content.Context
import android.util.Log
import com.gesturecontrol.domain.voice.Command
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable

private const val TAG = "VoiceLLM"

private val DETERMINISTIC_SAMPLER = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)

private val SYSTEM_INSTRUCTION = Contents.of(
    "You are a voice command interpreter for a drawing app. The user is speaking a short, " +
        "casual command. If it matches one of the available tools even loosely, call that " +
        "tool immediately -- do not respond with plain text. Call AT MOST ONE tool, then " +
        "stop -- never call a second, different tool in the same turn. Pick the single tool " +
        "whose name matches the color, size, or action mentioned; none of these tools take " +
        "arguments. " +
        "Examples: 'change the color to red' -> setColorRed(). 'switch to blue' -> " +
        "setColorCyan() (closest available color). 'make it green' -> setColorGreen(). 'use " +
        "yellow' -> setColorYellow(). 'draw in black' -> setColorBlack(). 'make the brush " +
        "bigger' -> setSizeLarge(). 'make the brush smaller' -> setSizeSmall(). 'medium " +
        "brush' -> setSizeMedium(). 'undo that' -> undo(). 'redo that' -> redo(). 'clear the " +
        "canvas' -> clear(). 'save this' -> save(). Only skip calling a tool if the request " +
        "clearly has nothing to do with drawing, colors, brush size, undo, redo, or saving.",
)

class VoiceCommandClassifier(private val context: Context) : Closeable {
    private var engine: Engine? = null

    fun isModelAvailable(): Boolean = VoiceModelConfig.modelFile(context).exists()

    suspend fun initialize() = withContext(Dispatchers.Default) {
        if (!isModelAvailable()) return@withContext

        val engineConfig = EngineConfig(
            modelPath = VoiceModelConfig.modelFile(context).absolutePath,
            backend = Backend.CPU(),
        )
        engine = Engine(engineConfig).also { it.initialize() }
    }

    suspend fun classify(transcript: String): Command = withContext(Dispatchers.Default) {
        Log.d(TAG, "classify() input transcript='$transcript'")
        val currentEngine = engine ?: run {
            Log.d(TAG, "no engine loaded, returning Unrecognized")
            return@withContext Command.Unrecognized
        }

        var result: Command = Command.Unrecognized
        var resultCaptured = false
        try {
            currentEngine.createConversation(
                ConversationConfig(
                    systemInstruction = SYSTEM_INSTRUCTION,
                    tools = listOf(
                        tool(
                            VoiceToolSet(
                                onCommand = { command ->
                                    if (resultCaptured) {
                                        Log.d(TAG, "ignoring extra tool call command=$command (already captured)")
                                    } else {
                                        Log.d(TAG, "tool call produced command=$command")
                                        result = command
                                        resultCaptured = true
                                    }
                                },
                            ),
                        ),
                    ),
                    samplerConfig = DETERMINISTIC_SAMPLER,
                    automaticToolCalling = true,
                ),
            ).use { conversation ->
                val reply = conversation.sendMessage(transcript)
                Log.d(TAG, "sendMessage returned reply='$reply'")
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "classify() threw", e)
        }
        Log.d(TAG, "classify() final result=$result")
        result
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
