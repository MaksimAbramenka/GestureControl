package com.gesturecontrol.domain.gesture

import com.gesturecontrol.domain.hand.NormalizedPoint

enum class InputState {
    IDLE,
    HOVER,
    DRAW_START,
    DRAW_MOVE,
    DRAW_END,
    ERASE,
}

data class InputCommand(
    val x: Float,
    val y: Float,
    val state: InputState,
    val pressure: Float,
    val timestampMs: Long,
)

class GestureInputMapper {
    companion object {
        private const val PRESSURE = 1f
    }

    private var isDrawing = false
    private var lastPosition: NormalizedPoint? = null

    fun map(
        gestureClass: GestureClass?,
        fingertip: NormalizedPoint?,
        timestampMs: Long,
    ): List<InputCommand> {
        if (gestureClass == null || fingertip == null) {
            val commands = endDrawIfNeeded(timestampMs)
            isDrawing = false
            lastPosition = null
            return commands
        }

        val commands = mutableListOf<InputCommand>()
        if (isDrawing && gestureClass != GestureClass.DRAW) {
            commands += InputCommand(fingertip.x, fingertip.y, InputState.DRAW_END, PRESSURE, timestampMs)
        }

        val state = when (gestureClass) {
            GestureClass.IDLE -> InputState.IDLE
            GestureClass.HOVER, GestureClass.FLING -> InputState.HOVER
            GestureClass.ERASE -> InputState.ERASE
            GestureClass.DRAW -> if (isDrawing) InputState.DRAW_MOVE else InputState.DRAW_START
        }
        commands += InputCommand(fingertip.x, fingertip.y, state, PRESSURE, timestampMs)

        isDrawing = gestureClass == GestureClass.DRAW
        lastPosition = fingertip
        return commands
    }

    private fun endDrawIfNeeded(timestampMs: Long): List<InputCommand> {
        val position = lastPosition
        return if (isDrawing && position != null) {
            listOf(InputCommand(position.x, position.y, InputState.DRAW_END, PRESSURE, timestampMs))
        } else {
            emptyList()
        }
    }
}
