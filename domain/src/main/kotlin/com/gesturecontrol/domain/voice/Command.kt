package com.gesturecontrol.domain.voice

enum class BrushColorName { CYAN, RED, GREEN, YELLOW, BLACK }

enum class BrushSizeName { SMALL, MEDIUM, LARGE }

sealed class Command {
    data class SetBrushColor(val color: BrushColorName) : Command()
    data class SetBrushSize(val size: BrushSizeName) : Command()
    object Undo : Command()
    object Redo : Command()
    object Clear : Command()
    object Save : Command()
    object StartContinuousListening : Command()
    object StopContinuousListening : Command()
    object Unrecognized : Command()
}
