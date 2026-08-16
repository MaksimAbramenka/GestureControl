package com.gesturecontrol.domain.gesture

import com.gesturecontrol.domain.hand.NormalizedPoint
import kotlin.test.Test
import kotlin.test.assertEquals

class GestureInputMapperTest {
    private val point = NormalizedPoint(x = 0.3f, y = 0.4f, z = 0f)
    private val otherPoint = NormalizedPoint(x = 0.6f, y = 0.7f, z = 0f)

    @Test
    fun `IDLE maps to a single IDLE command`() {
        val mapper = GestureInputMapper()

        val commands = mapper.map(GestureClass.IDLE, point, timestampMs = 100L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.IDLE, 1f, 100L)), commands)
    }

    @Test
    fun `HOVER maps to a single HOVER command`() {
        val mapper = GestureInputMapper()

        val commands = mapper.map(GestureClass.HOVER, point, timestampMs = 100L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.HOVER, 1f, 100L)), commands)
    }

    @Test
    fun `ERASE maps to a single ERASE command`() {
        val mapper = GestureInputMapper()

        val commands = mapper.map(GestureClass.ERASE, point, timestampMs = 100L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.ERASE, 1f, 100L)), commands)
    }

    @Test
    fun `the first DRAW frame maps to DRAW_START`() {
        val mapper = GestureInputMapper()

        val commands = mapper.map(GestureClass.DRAW, point, timestampMs = 100L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.DRAW_START, 1f, 100L)), commands)
    }

    @Test
    fun `a subsequent DRAW frame maps to DRAW_MOVE`() {
        val mapper = GestureInputMapper()
        mapper.map(GestureClass.DRAW, point, timestampMs = 100L)

        val commands = mapper.map(GestureClass.DRAW, otherPoint, timestampMs = 200L)

        assertEquals(listOf(InputCommand(0.6f, 0.7f, InputState.DRAW_MOVE, 1f, 200L)), commands)
    }

    @Test
    fun `leaving DRAW for another gesture emits DRAW_END then the new state`() {
        val mapper = GestureInputMapper()
        mapper.map(GestureClass.DRAW, point, timestampMs = 100L)

        val commands = mapper.map(GestureClass.HOVER, otherPoint, timestampMs = 200L)

        assertEquals(
            listOf(
                InputCommand(0.6f, 0.7f, InputState.DRAW_END, 1f, 200L),
                InputCommand(0.6f, 0.7f, InputState.HOVER, 1f, 200L),
            ),
            commands,
        )
    }

    @Test
    fun `losing hand tracking mid-draw emits DRAW_END at the last known position`() {
        val mapper = GestureInputMapper()
        mapper.map(GestureClass.DRAW, point, timestampMs = 100L)

        val commands = mapper.map(gestureClass = null, fingertip = null, timestampMs = 200L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.DRAW_END, 1f, 200L)), commands)
    }

    @Test
    fun `losing hand tracking while not drawing emits nothing`() {
        val mapper = GestureInputMapper()
        mapper.map(GestureClass.IDLE, point, timestampMs = 100L)

        val commands = mapper.map(gestureClass = null, fingertip = null, timestampMs = 200L)

        assertEquals(emptyList<InputCommand>(), commands)
    }

    @Test
    fun `drawing resumes with DRAW_START after a gap`() {
        val mapper = GestureInputMapper()
        mapper.map(GestureClass.DRAW, point, timestampMs = 100L)
        mapper.map(GestureClass.IDLE, point, timestampMs = 200L)

        val commands = mapper.map(GestureClass.DRAW, point, timestampMs = 300L)

        assertEquals(listOf(InputCommand(0.3f, 0.4f, InputState.DRAW_START, 1f, 300L)), commands)
    }
}
