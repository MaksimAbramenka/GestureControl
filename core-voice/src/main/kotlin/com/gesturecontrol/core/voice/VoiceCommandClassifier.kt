package com.gesturecontrol.core.voice

import android.content.Context
import com.gesturecontrol.domain.voice.Command
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import java.io.Closeable

private val DETERMINISTIC_SAMPLER = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0)

private val SYSTEM_INSTRUCTION = Contents.of(
    "You are a voice command interpreter for a drawing app. The user is speaking a short, " +
        "casual command. If it matches one of the available tools even loosely, call that " +
        "tool immediately with the correct arguments -- do not respond with plain text. " +
        "Examples: 'change the color to red' -> setBrushColor(color='red'). 'make the brush " +
        "bigger' -> setBrushSize(size='large'). 'undo that' -> undo(). 'clear the canvas' -> " +
        "clear(). 'save this' -> save(). 'start listening' -> " +
        "setContinuousListening(enabled=true). 'stop listening' -> " +
        "setContinuousListening(enabled=false). Only skip calling a tool if the request " +
        "clearly has nothing to do with drawing, colors, brush size, undo, redo, saving, or " +
        "listening mode.",
)

class VoiceCommandClassifier(private val context: Context) : Closeable {
    private var engine: Engine? = null

    fun isModelAvailable(): Boolean = VoiceModelConfig.modelFile(context).exists()

    suspend fun initialize() {
        if (!isModelAvailable()) return
        val engineConfig = EngineConfig(
            modelPath = VoiceModelConfig.modelFile(context).absolutePath,
            backend = Backend.CPU(),
        )
        engine = Engine(engineConfig).also { it.initialize() }
    }

    suspend fun classify(transcript: String): Command {
        val currentEngine = engine ?: return Command.Unrecognized

        var result: Command = Command.Unrecognized
        val conversation = currentEngine.createConversation(
            ConversationConfig(
                systemInstruction = SYSTEM_INSTRUCTION,
                tools = listOf(tool(VoiceToolSet(onCommand = { result = it }))),
                samplerConfig = DETERMINISTIC_SAMPLER,
                automaticToolCalling = true,
            ),
        )
        conversation.sendMessage(transcript)
        return result
    }

    override fun close() {
        engine?.close()
        engine = null
    }
}
