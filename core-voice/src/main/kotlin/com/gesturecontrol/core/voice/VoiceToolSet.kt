package com.gesturecontrol.core.voice

import com.gesturecontrol.domain.voice.BrushColorName
import com.gesturecontrol.domain.voice.BrushSizeName
import com.gesturecontrol.domain.voice.Command
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

class VoiceToolSet(private val onCommand: (Command) -> Unit) : ToolSet {
    @Tool(description = "Change the drawing brush color.")
    fun setBrushColor(
        @ToolParam(description = "One of: cyan, red, green, yellow, black") color: String,
    ) {
        val parsed = BrushColorName.entries.firstOrNull { it.name.equals(color, ignoreCase = true) }
        onCommand(if (parsed != null) Command.SetBrushColor(parsed) else Command.Unrecognized)
    }

    @Tool(description = "Change the drawing brush size.")
    fun setBrushSize(
        @ToolParam(description = "One of: small, medium, large") size: String,
    ) {
        val parsed = BrushSizeName.entries.firstOrNull { it.name.equals(size, ignoreCase = true) }
        onCommand(if (parsed != null) Command.SetBrushSize(parsed) else Command.Unrecognized)
    }

    @Tool(description = "Undo the last drawing action.")
    fun undo() = onCommand(Command.Undo)

    @Tool(description = "Redo the last undone drawing action.")
    fun redo() = onCommand(Command.Redo)

    @Tool(description = "Clear the entire canvas.")
    fun clear() = onCommand(Command.Clear)

    @Tool(description = "Save the current drawing and open the share sheet.")
    fun save() = onCommand(Command.Save)

    @Tool(description = "Turn continuous hands-free voice listening on or off.")
    fun setContinuousListening(
        @ToolParam(description = "true to start continuous listening, false to stop it") enabled: Boolean,
    ) {
        onCommand(if (enabled) Command.StartContinuousListening else Command.StopContinuousListening)
    }
}
