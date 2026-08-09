package com.gesturecontrol.core.ml

import com.gesturecontrol.domain.hand.Handedness
import com.gesturecontrol.domain.hand.ImageDimensions
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class HandLandmarksMapperTest {
    private val someImageDimensions = ImageDimensions(width = 640, height = 480)
    private val someFps = 30f

    private fun fakeHandLandmarks(count: Int = 21): List<NormalizedLandmark> = (0 until count).map { i ->
        NormalizedLandmark.create(i / 100f, i / 200f, i / 300f)
    }

    private fun fakeHandedness(label: String): List<Category> = listOf(Category.create(0.99f, 0, label, label))

    @Test
    fun `maps no detected hands to an empty result`() {
        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = emptyList(),
            mediapipeHandedness = emptyList(),
            timestampMs = 1234L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = false,
        )

        assertTrue(result.hands.isEmpty())
        assertEquals(1234L, result.timestampMs)
        assertEquals(someImageDimensions, result.imageDimensions)
        assertEquals(someFps, result.fps)
    }

    @Test
    fun `maps a single detected hand preserving landmark order and coordinates`() {
        val mediapipeHand = fakeHandLandmarks()

        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = listOf(mediapipeHand),
            mediapipeHandedness = listOf(fakeHandedness("Right")),
            timestampMs = 42L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = false,
        )

        assertEquals(1, result.hands.size)
        val mappedPoints = result.hands.single().points
        assertEquals(21, mappedPoints.size)
        mediapipeHand.forEachIndexed { index, source ->
            assertEquals(source.x(), mappedPoints[index].x)
            assertEquals(source.y(), mappedPoints[index].y)
            assertEquals(source.z(), mappedPoints[index].z)
        }
    }

    @Test
    fun `maps two detected hands independently`() {
        val firstHand = fakeHandLandmarks()
        val secondHand = fakeHandLandmarks().map { NormalizedLandmark.create(it.x() + 1f, it.y(), it.z()) }

        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = listOf(firstHand, secondHand),
            mediapipeHandedness = listOf(fakeHandedness("Right"), fakeHandedness("Left")),
            timestampMs = 0L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = false,
        )

        assertEquals(2, result.hands.size)
        assertEquals(firstHand.first().x(), result.hands[0].points.first().x)
        assertEquals(secondHand.first().x(), result.hands[1].points.first().x)
    }

    @Test
    fun `propagates a malformed hand with the wrong landmark count as a failure`() {
        assertThrows<IllegalArgumentException> {
            HandLandmarksMapper.toDomain(
                mediapipeHands = listOf(fakeHandLandmarks(count = 5)),
                mediapipeHandedness = listOf(fakeHandedness("Right")),
                timestampMs = 0L,
                imageDimensions = someImageDimensions,
                fps = someFps,
                mirrored = false,
            )
        }
    }

    @Test
    fun `unmirrored input maps MediaPipe's handedness label directly`() {
        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = listOf(fakeHandLandmarks()),
            mediapipeHandedness = listOf(fakeHandedness("Right")),
            timestampMs = 0L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = false,
        )

        assertEquals(Handedness.RIGHT, result.hands.single().handedness)
    }

    @Test
    fun `mirrored input flips MediaPipe's handedness label`() {
        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = listOf(fakeHandLandmarks()),
            mediapipeHandedness = listOf(fakeHandedness("Right")),
            timestampMs = 0L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = true,
        )

        assertEquals(Handedness.LEFT, result.hands.single().handedness)
    }

    @Test
    fun `an unrecognized or missing handedness label maps to null`() {
        val result = HandLandmarksMapper.toDomain(
            mediapipeHands = listOf(fakeHandLandmarks()),
            mediapipeHandedness = listOf(emptyList()),
            timestampMs = 0L,
            imageDimensions = someImageDimensions,
            fps = someFps,
            mirrored = false,
        )

        assertNull(result.hands.single().handedness)
    }
}
