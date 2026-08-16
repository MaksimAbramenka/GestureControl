package com.gesturecontrol.domain.hand

import kotlin.math.max

data class ImageDimensions(
    val width: Int,
    val height: Int,
)

data class ViewportDimensions(
    val width: Float,
    val height: Float,
)

data class ViewportPoint(
    val x: Float,
    val y: Float,
)

fun NormalizedPoint.toViewportPoint(
    imageDimensions: ImageDimensions,
    viewportDimensions: ViewportDimensions,
    mirrored: Boolean,
): ViewportPoint {
    require(imageDimensions.width > 0 && imageDimensions.height > 0) {
        "Image dimensions must be positive, got $imageDimensions"
    }
    require(viewportDimensions.width > 0 && viewportDimensions.height > 0) {
        "Viewport dimensions must be positive, got $viewportDimensions"
    }

    val scale = max(
        viewportDimensions.width / imageDimensions.width,
        viewportDimensions.height / imageDimensions.height,
    )

    val scaledImageWidth = imageDimensions.width * scale
    val scaledImageHeight = imageDimensions.height * scale

    val offsetX = (viewportDimensions.width - scaledImageWidth) / 2f
    val offsetY = (viewportDimensions.height - scaledImageHeight) / 2f

    val normalizedX = if (mirrored) 1f - this.x else this.x

    return ViewportPoint(
        x = normalizedX * scaledImageWidth + offsetX,
        y = this.y * scaledImageHeight + offsetY,
    )
}

fun NormalizedPoint.toViewportNormalizedPoint(
    imageDimensions: ImageDimensions,
    viewportDimensions: ViewportDimensions,
    mirrored: Boolean,
): NormalizedPoint {
    val viewportPoint = toViewportPoint(imageDimensions, viewportDimensions, mirrored)
    return NormalizedPoint(
        x = (viewportPoint.x / viewportDimensions.width).coerceIn(0f, 1f),
        y = (viewportPoint.y / viewportDimensions.height).coerceIn(0f, 1f),
        z = this.z,
    )
}
