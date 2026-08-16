package com.gesturecontrol.domain.hand

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LandmarkViewportMapperTest {
    private val tolerance = 0.001f

    @Test
    fun `square image in square viewport maps center to center`() {
        val result = NormalizedPoint(x = 0.5f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 100),
            viewportDimensions = ViewportDimensions(width = 200f, height = 200f),
            mirrored = false,
        )

        assertEquals(100f, result.x, tolerance)
        assertEquals(100f, result.y, tolerance)
    }

    @Test
    fun `square image in square viewport maps corners to corners`() {
        val topLeft = NormalizedPoint(x = 0f, y = 0f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 100),
            viewportDimensions = ViewportDimensions(width = 200f, height = 200f),
            mirrored = false,
        )
        assertEquals(0f, topLeft.x, tolerance)
        assertEquals(0f, topLeft.y, tolerance)

        val bottomRight = NormalizedPoint(x = 1f, y = 1f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 100),
            viewportDimensions = ViewportDimensions(width = 200f, height = 200f),
            mirrored = false,
        )
        assertEquals(200f, bottomRight.x, tolerance)
        assertEquals(200f, bottomRight.y, tolerance)
    }

    @Test
    fun `wide image in square viewport crops left and right -- center still maps to center`() {
        // image aspect 2:1 (e.g. landscape sensor output), viewport is square -> horizontal crop
        val result = NormalizedPoint(x = 0.5f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 200, height = 100),
            viewportDimensions = ViewportDimensions(width = 100f, height = 100f),
            mirrored = false,
        )

        assertEquals(50f, result.x, tolerance)
        assertEquals(50f, result.y, tolerance)
    }

    @Test
    fun `wide image in square viewport clips left edge of image out of view`() {
        // scale = max(100/200, 100/100) = 1.0 -> scaled image is 200x100, offsetX = -50
        // image x=0 (left edge) should land off-screen to the left of the viewport
        val result = NormalizedPoint(x = 0f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 200, height = 100),
            viewportDimensions = ViewportDimensions(width = 100f, height = 100f),
            mirrored = false,
        )

        assertEquals(-50f, result.x, tolerance)
    }

    @Test
    fun `tall image in wide viewport crops top and bottom`() {
        val result = NormalizedPoint(x = 0.5f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 200),
            viewportDimensions = ViewportDimensions(width = 100f, height = 100f),
            mirrored = false,
        )

        assertEquals(50f, result.x, tolerance)
        assertEquals(50f, result.y, tolerance)
    }

    @Test
    fun `mirrored front camera flips x axis`() {
        val unmirrored = NormalizedPoint(x = 0.2f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 100),
            viewportDimensions = ViewportDimensions(width = 100f, height = 100f),
            mirrored = false,
        )
        val mirrored = NormalizedPoint(x = 0.2f, y = 0.5f, z = 0f).toViewportPoint(
            imageDimensions = ImageDimensions(width = 100, height = 100),
            viewportDimensions = ViewportDimensions(width = 100f, height = 100f),
            mirrored = true,
        )

        assertEquals(20f, unmirrored.x, tolerance)
        assertEquals(80f, mirrored.x, tolerance)
        assertEquals(unmirrored.y, mirrored.y, tolerance)
    }

    @Test
    fun `viewport-normalized center maps to 0-5 by 0-5 regardless of aspect mismatch`() {
        val result = NormalizedPoint(x = 0.5f, y = 0.5f, z = 0f).toViewportNormalizedPoint(
            imageDimensions = ImageDimensions(width = 640, height = 480),
            viewportDimensions = ViewportDimensions(width = 1080f, height = 2280f),
            mirrored = false,
        )

        assertEquals(0.5f, result.x, tolerance)
        assertEquals(0.5f, result.y, tolerance)
    }

    @Test
    fun `viewport-normalized point clamps to the edge when cropped out of view`() {
        val leftEdge = NormalizedPoint(x = 0f, y = 0.5f, z = 0f).toViewportNormalizedPoint(
            imageDimensions = ImageDimensions(width = 640, height = 480),
            viewportDimensions = ViewportDimensions(width = 1080f, height = 2280f),
            mirrored = false,
        )
        val rightEdge = NormalizedPoint(x = 1f, y = 0.5f, z = 0f).toViewportNormalizedPoint(
            imageDimensions = ImageDimensions(width = 640, height = 480),
            viewportDimensions = ViewportDimensions(width = 1080f, height = 2280f),
            mirrored = false,
        )

        assertEquals(0f, leftEdge.x, tolerance)
        assertEquals(1f, rightEdge.x, tolerance)
    }

    @Test
    fun `rejects non-positive dimensions`() {
        data class Case(val imageWidth: Int, val imageHeight: Int, val viewportWidth: Float, val viewportHeight: Float)
        val cases = listOf(
            Case(0, 100, 100f, 100f),
            Case(100, 0, 100f, 100f),
            Case(100, 100, 0f, 100f),
            Case(100, 100, 100f, 0f),
            Case(-1, 100, 100f, 100f),
        )

        cases.forEach { case ->
            assertFailsWith<IllegalArgumentException>("expected failure for $case") {
                NormalizedPoint(x = 0.5f, y = 0.5f, z = 0f).toViewportPoint(
                    imageDimensions = ImageDimensions(width = case.imageWidth, height = case.imageHeight),
                    viewportDimensions = ViewportDimensions(width = case.viewportWidth, height = case.viewportHeight),
                    mirrored = false,
                )
            }
        }
    }
}
