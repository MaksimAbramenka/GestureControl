package com.gesturecontrol.core.engine

import com.gesturecontrol.domain.gesture.InputCommand
import com.gesturecontrol.domain.gesture.InputState

object InputEventState {
    const val IDLE = 0
    const val HOVER = 1
    const val DRAW_START = 2
    const val DRAW_MOVE = 3
    const val DRAW_END = 4
    const val ERASE = 5
}

private fun InputState.toNativeInt(): Int =
    when (this) {
        InputState.IDLE -> InputEventState.IDLE
        InputState.HOVER -> InputEventState.HOVER
        InputState.DRAW_START -> InputEventState.DRAW_START
        InputState.DRAW_MOVE -> InputEventState.DRAW_MOVE
        InputState.DRAW_END -> InputEventState.DRAW_END
        InputState.ERASE -> InputEventState.ERASE
    }

fun NativeEngine.submit(command: InputCommand) {
    nativeSubmitInput(command.x, command.y, command.state.toNativeInt(), command.pressure, command.timestampMs)
}
