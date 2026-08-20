package com.gesturecontrol.core.ui.camera

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.gesturecontrol.domain.hand.HandDetectionResult
import kotlin.math.roundToInt

const val PIP_MIN_SIZE_FRACTION = 0.25f
const val PIP_MAX_SIZE_FRACTION = 0.5f
const val PIP_DEFAULT_SIZE_FRACTION = 0.3f

const val PIP_ASPECT_RATIO = 4f / 3f

private val PipCornerRadius = 12.dp
private val PipEdgePadding = 16.dp

/**
 * A draggable, pinch-resizable picture-in-picture camera view, meant to be the topmost overlay in
 * the composition so it always wins touch first. The native drawing surface renders through its
 * own always-on-top platform surface regardless of Compose ordering, so drawn strokes still show
 * up over this preview rather than being hidden behind it.
 */
@Composable
fun DraggableCameraPreview(
    surfaceRequest: SurfaceRequest?,
    handDetectionResult: HandDetectionResult,
    mirrored: Boolean,
    viewportSizePx: IntSize,
    offset: Offset?,
    onOffsetChange: (Offset) -> Unit,
    sizeFraction: Float,
    onSizeFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (viewportSizePx.width <= 0 || viewportSizePx.height <= 0) return

    val density = LocalDensity.current
    val widthPx = sizeFraction * viewportSizePx.width
    val heightPx = widthPx * PIP_ASPECT_RATIO
    val edgePaddingPx = with(density) { PipEdgePadding.toPx() }

    LaunchedEffect(viewportSizePx, offset == null) {
        if (offset == null) {
            onOffsetChange(
                Offset(
                    x = edgePaddingPx,
                    y = (viewportSizePx.height - heightPx - edgePaddingPx).coerceAtLeast(0f),
                ),
            )
        }
    }
    val currentOffset = offset ?: return

    val maxOffsetX = (viewportSizePx.width - widthPx).coerceAtLeast(0f)
    val maxOffsetY = (viewportSizePx.height - heightPx).coerceAtLeast(0f)
    val clampedOffset = Offset(
        x = currentOffset.x.coerceIn(0f, maxOffsetX),
        y = currentOffset.y.coerceIn(0f, maxOffsetY),
    )

    val latestOffset = rememberUpdatedState(currentOffset)
    val latestSizeFraction = rememberUpdatedState(sizeFraction)
    val latestViewportSize = rememberUpdatedState(viewportSizePx)

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .offset { IntOffset(clampedOffset.x.roundToInt(), clampedOffset.y.roundToInt()) }
                .size(width = with(density) { widthPx.toDp() }, height = with(density) { heightPx.toDp() })
                .clip(RoundedCornerShape(PipCornerRadius))
                .background(Color.Black)
                .border(2.dp, Color.White, RoundedCornerShape(PipCornerRadius))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val vp = latestViewportSize.value
                        val newFraction = (latestSizeFraction.value * zoom)
                            .coerceIn(PIP_MIN_SIZE_FRACTION, PIP_MAX_SIZE_FRACTION)
                        onSizeFractionChange(newFraction)

                        val w = newFraction * vp.width
                        val h = w * PIP_ASPECT_RATIO
                        val newOffset = Offset(
                            x = (latestOffset.value.x + pan.x).coerceIn(0f, (vp.width - w).coerceAtLeast(0f)),
                            y = (latestOffset.value.y + pan.y).coerceIn(0f, (vp.height - h).coerceAtLeast(0f)),
                        )
                        onOffsetChange(newOffset)
                    }
                },
        ) {
            surfaceRequest?.let { request ->
                CameraXViewfinder(
                    surfaceRequest = request,
                    implementationMode = ImplementationMode.EMBEDDED,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            HandLandmarkOverlay(
                handDetectionResult = handDetectionResult,
                mirrored = mirrored,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
