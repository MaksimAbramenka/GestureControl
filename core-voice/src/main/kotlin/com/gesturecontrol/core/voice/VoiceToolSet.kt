package com.gesturecontrol.core.voice

import com.gesturecontrol.domain.voice.BrushColorName
import com.gesturecontrol.domain.voice.BrushSizeName
import com.gesturecontrol.domain.voice.Command
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolSet

class VoiceToolSet(private val onCommand: (Command) -> Unit) : ToolSet {
    @Tool(description = "Change the drawing brush color to cyan.")
    fun setColorCyan() = onCommand(Command.SetBrushColor(BrushColorName.CYAN))

    @Tool(description = "Change the drawing brush color to red.")
    fun setColorRed() = onCommand(Command.SetBrushColor(BrushColorName.RED))

    @Tool(description = "Change the drawing brush color to green.")
    fun setColorGreen() = onCommand(Command.SetBrushColor(BrushColorName.GREEN))

    @Tool(description = "Change the drawing brush color to yellow.")
    fun setColorYellow() = onCommand(Command.SetBrushColor(BrushColorName.YELLOW))

    @Tool(description = "Change the drawing brush color to black.")
    fun setColorBlack() = onCommand(Command.SetBrushColor(BrushColorName.BLACK))

    @Tool(description = "Change the drawing brush size to small.")
    fun setSizeSmall() = onCommand(Command.SetBrushSize(BrushSizeName.SMALL))

    @Tool(description = "Change the drawing brush size to medium.")
    fun setSizeMedium() = onCommand(Command.SetBrushSize(BrushSizeName.MEDIUM))

    @Tool(description = "Change the drawing brush size to large.")
    fun setSizeLarge() = onCommand(Command.SetBrushSize(BrushSizeName.LARGE))

    @Tool(description = "Undo the last drawing action.")
    fun undo() = onCommand(Command.Undo)

    @Tool(description = "Redo the last undone drawing action.")
    fun redo() = onCommand(Command.Redo)

    @Tool(description = "Clear the entire canvas.")
    fun clear() = onCommand(Command.Clear)

    @Tool(description = "Save the current drawing and open the share sheet.")
    fun save() = onCommand(Command.Save)
}
