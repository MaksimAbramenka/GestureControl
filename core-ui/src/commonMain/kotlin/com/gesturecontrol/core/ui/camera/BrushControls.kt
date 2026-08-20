package com.gesturecontrol.core.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

data class BrushColorOption(
    val label: String,
    val r: Float,
    val g: Float,
    val b: Float,
) {
    val composeColor: Color get() = Color(r, g, b)
}

val BRUSH_COLOR_OPTIONS = listOf(
    BrushColorOption("Cyan", 0.1f, 0.9f, 1.0f),
    BrushColorOption("Red", 1.0f, 0.2f, 0.2f),
    BrushColorOption("Green", 0.2f, 1.0f, 0.3f),
    BrushColorOption("Yellow", 1.0f, 0.9f, 0.1f),
    // Not white -- the canvas background is white, so a white stroke would be invisible.
    BrushColorOption("Black", 0.0f, 0.0f, 0.0f),
)

enum class BrushSizeOption(val label: String, val size: Float, val dotSize: Dp) {
    SMALL("S", 0.008f, 10.dp),
    MEDIUM("M", 0.015f, 18.dp),
    LARGE("L", 0.03f, 26.dp),
}

private val CarouselItemSize = 56.dp
private val CarouselTrackWidth = 200.dp
private val SelectionRingSize = 46.dp

@Composable
fun BrushControls(
    selectedColor: BrushColorOption,
    selectedSize: BrushSizeOption,
    onSelectColor: (BrushColorOption) -> Unit,
    onSelectSize: (BrushSizeOption) -> Unit,
    modifier: Modifier = Modifier,
    onColorCarouselControllerReady: (CarouselController) -> Unit = {},
    onSizeCarouselControllerReady: (CarouselController) -> Unit = {},
    colorCarouselActiveEdge: Int? = null,
    sizeCarouselActiveEdge: Int? = null,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SelectionCarousel(
            BRUSH_COLOR_OPTIONS,
            selectedColor,
            onSelectColor,
            onColorCarouselControllerReady,
            colorCarouselActiveEdge,
        ) { option ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(option.composeColor),
            )
        }
        SelectionCarousel(
            BrushSizeOption.entries,
            selectedSize,
            onSelectSize,
            onSizeCarouselControllerReady,
            sizeCarouselActiveEdge,
        ) { option ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = option.label, color = Color.White, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .size(option.dotSize)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

/** Lets an owner outside [SelectionCarousel] hit-test a screen position against its current
 * on-screen edge bands and command it to step the selection by one item, for gesture-driven
 * input (dwelling over an edge) that can't reach the carousel through normal touch/scroll. */
class CarouselController internal constructor(
    private val listState: LazyListState,
    private val itemCount: Int,
) {
    companion object {
        private const val EDGE_ZONE_FRACTION = 0.3f
    }

    internal var coordinates: LayoutCoordinates? = null

    /** -1 if [pointInRoot] is over this carousel's left edge band, +1 for the right edge band,
     * or null if it's outside the carousel entirely or in the dead middle zone. */
    fun edgeZoneAt(root: LayoutCoordinates, pointInRoot: Offset): Int? {
        val local = coordinates ?: return null
        if (!local.isAttached || !root.isAttached) return null

        val topLeft = root.localPositionOf(local, Offset.Zero)
        val rect = Rect(topLeft, local.size.toSize())
        if (!rect.contains(pointInRoot)) return null

        val fraction = (pointInRoot.x - rect.left) / rect.width
        return when {
            fraction <= EDGE_ZONE_FRACTION -> -1
            fraction >= 1f - EDGE_ZONE_FRACTION -> 1
            else -> null
        }
    }

    /** [indexDelta] is typically +1 (next item) or -1 (previous item). */
    suspend fun step(indexDelta: Int) {
        val current = centerMostVisibleIndex(listState) ?: return
        val next = (current + indexDelta).coerceIn(0, itemCount - 1)
        if (next != current) listState.centerOnItem(next)
    }
}

/** A horizontally snapping picker: the centered item is the selection, reachable by dragging,
 * flinging, or tapping any item directly to jump straight to it. [activeEdge] highlights the
 * left (-1) or right (+1) chevron to reflect a caller-driven dwell-to-step interaction. */
@Composable
private fun <T> SelectionCarousel(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    onControllerReady: (CarouselController) -> Unit,
    activeEdge: Int?,
    itemContent: @Composable BoxScope.(T) -> Unit,
) {
    val density = LocalDensity.current
    val itemPx = with(density) { CarouselItemSize.toPx() }
    val sidePadding = (CarouselTrackWidth - CarouselItemSize) / 2
    val listState = rememberLazyListState(
        initialFirstVisibleItemScrollOffset = (items.indexOf(selected) * itemPx).roundToInt(),
    )
    val coroutineScope = rememberCoroutineScope()
    val controller = remember(listState) { CarouselController(listState, items.size) }
    LaunchedEffect(controller) { onControllerReady(controller) }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.onGloballyPositioned { controller.coordinates = it },
    ) {
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState, SnapPosition.Center),
            contentPadding = PaddingValues(horizontal = sidePadding),
            modifier = Modifier.width(CarouselTrackWidth),
        ) {
            itemsIndexed(items) { index, item ->
                Box(
                    modifier = Modifier
                        .size(CarouselItemSize)
                        .graphicsLayer {
                            val (scale, itemAlpha) = centerEmphasis(listState, index, itemPx)
                            scaleX = scale
                            scaleY = scale
                            alpha = itemAlpha
                        }
                        .clickable {
                            onSelect(item)
                            coroutineScope.launch { listState.centerOnItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    itemContent(item)
                }
            }
        }

        Box(
            modifier = Modifier
                .size(SelectionRingSize)
                .clip(CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )

        Text(
            text = "‹",
            color = Color.White.copy(alpha = if (activeEdge == -1) 1f else 0.25f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = "›",
            color = Color.White.copy(alpha = if (activeEdge == 1) 1f else 0.25f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                centerMostVisibleIndex(listState)?.let { onSelect(items[it]) }
            }
    }

    LaunchedEffect(selected) {
        val targetIndex = items.indexOf(selected)
        if (targetIndex >= 0 && centerMostVisibleIndex(listState) != targetIndex) {
            listState.centerOnItem(targetIndex)
        }
    }
}

private fun centerEmphasis(state: LazyListState, index: Int, itemSizePx: Float): Pair<Float, Float> {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return 0.7f to 0.4f
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    val itemCenter = item.offset + item.size / 2f
    val falloff = (1f - abs(itemCenter - viewportCenter) / itemSizePx).coerceIn(0f, 1f)
    return (0.7f + 0.35f * falloff) to (0.4f + 0.6f * falloff)
}

private fun centerMostVisibleIndex(state: LazyListState): Int? {
    val info = state.layoutInfo
    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }?.index
}

private suspend fun LazyListState.centerOnItem(index: Int) {
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        animateScrollToItem(index)
        return
    }
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = item.offset + item.size / 2
    animateScrollBy((itemCenter - viewportCenter).toFloat())
}
