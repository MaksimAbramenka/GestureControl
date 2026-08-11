package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.hand.NormalizedPoint

private val HoverDotRadius = 8.dp
private val DrawDotRadius = 14.dp
private val CursorStrokeWidth = 1.5.dp
private val EraserWidth = 40.dp
private val EraserHeight = 26.dp
private val EraserCornerRadius = 6.dp
private val EraserBandHeight = 8.dp
private val EraserRotationDegrees = -25f
private val FlingChevronSpan = 26.dp
private val FlingChevronHeight = 16.dp
private val FlingColor = Color(0xFF7B61FF)

/** Shows where the tracked fingertip currently is, styled by the active gesture so IDLE/HOVER,
 * an active DRAW and ERASE are each visually distinct without having to watch the camera feed. */
@Composable
fun GestureCursorOverlay(
    fingertip: NormalizedPoint?,
    gestureClass: GestureClass?,
    brushColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (fingertip == null || gestureClass == null) return@Canvas
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val center = Offset(fingertip.x * size.width, fingertip.y * size.height)

        when (gestureClass) {
            GestureClass.IDLE, GestureClass.HOVER -> drawHoverDot(center)
            GestureClass.DRAW -> drawActiveDot(center, brushColor)
            GestureClass.ERASE -> drawEraserIcon(center)
            GestureClass.FLING -> drawFlingIcon(center)
        }
    }
}

private fun DrawScope.drawHoverDot(center: Offset) {
    val radius = HoverDotRadius.toPx()
    drawCircle(color = Color.White.copy(alpha = 0.5f), radius = radius, center = center)
    drawCircle(
        color = Color.Black.copy(alpha = 0.5f),
        radius = radius,
        center = center,
        style = Stroke(width = CursorStrokeWidth.toPx()),
    )
}

private fun DrawScope.drawActiveDot(center: Offset, brushColor: Color) {
    val radius = DrawDotRadius.toPx()
    drawCircle(color = brushColor, radius = radius, center = center)
    drawCircle(
        color = Color.Black.copy(alpha = 0.35f),
        radius = radius,
        center = center,
        style = Stroke(width = CursorStrokeWidth.toPx()),
    )
}

private fun DrawScope.drawEraserIcon(center: Offset) {
    val width = EraserWidth.toPx()
    val height = EraserHeight.toPx()
    val cornerRadius = CornerRadius(EraserCornerRadius.toPx())
    val topLeft = Offset(center.x - width / 2f, center.y - height / 2f)

    rotate(degrees = EraserRotationDegrees, pivot = center) {
        drawRoundRect(
            color = Color(0xFFE8A6C1),
            topLeft = topLeft,
            size = Size(width, height),
            cornerRadius = cornerRadius,
        )
        drawRoundRect(
            color = Color(0xFFD888AC),
            topLeft = Offset(topLeft.x, topLeft.y + height - EraserBandHeight.toPx()),
            size = Size(width, EraserBandHeight.toPx()),
            cornerRadius = CornerRadius(EraserCornerRadius.toPx() / 2f),
        )
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.35f),
            topLeft = topLeft,
            size = Size(width, height),
            cornerRadius = cornerRadius,
            style = Stroke(width = CursorStrokeWidth.toPx()),
        )
    }
}

private fun DrawScope.drawFlingIcon(center: Offset) {
    val halfSpan = FlingChevronSpan.toPx()
    val headSize = FlingChevronHeight.toPx() / 2f
    val strokeWidth = CursorStrokeWidth.toPx() * 2.2f
    val strokeStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val leftEnd = Offset(center.x - halfSpan, center.y)
    val rightEnd = Offset(center.x + halfSpan, center.y)

    val arrow = Path().apply {
        moveTo(leftEnd.x, leftEnd.y)
        lineTo(rightEnd.x, rightEnd.y)
        moveTo(leftEnd.x + headSize, leftEnd.y - headSize)
        lineTo(leftEnd.x, leftEnd.y)
        lineTo(leftEnd.x + headSize, leftEnd.y + headSize)
        moveTo(rightEnd.x - headSize, rightEnd.y - headSize)
        lineTo(rightEnd.x, rightEnd.y)
        lineTo(rightEnd.x - headSize, rightEnd.y + headSize)
    }
    drawPath(arrow, color = FlingColor, style = strokeStyle)
}
