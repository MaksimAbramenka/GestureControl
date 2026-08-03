package com.gesturecontrol.domain.gesture

import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.NormalizedPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.math.sqrt

class HandFeatureExtractorTest {
    private val tolerance = 0.001f
    private val middleMcpIndex = 9

    private fun handWithWristAndMiddleMcp(
        wrist: NormalizedPoint,
        middleMcp: NormalizedPoint,
    ): HandLandmarks {
        val points = (0 until HandLandmarks.LANDMARK_COUNT).map { i ->
            when (i) {
                0 -> wrist
                middleMcpIndex -> middleMcp
                else -> NormalizedPoint(x = i * 0.1f, y = i * 0.2f, z = i * 0.05f)
            }
        }
        return HandLandmarks(points)
    }

    @Test
    fun `output has one value per landmark axis`() {
        val hand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0f, 0f, 0f),
            middleMcp = NormalizedPoint(1f, 0f, 0f),
        )

        val features = HandFeatureExtractor.extractFeatures(hand)

        assertEquals(HandLandmarks.LANDMARK_COUNT * 3, features.size)
    }

    @Test
    fun `wrist maps to the origin`() {
        val hand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0.4f, 0.5f, 0.1f),
            middleMcp = NormalizedPoint(0.4f, 0.3f, 0.1f),
        )

        val features = HandFeatureExtractor.extractFeatures(hand)

        assertEquals(0f, features[0], tolerance)
        assertEquals(0f, features[1], tolerance)
        assertEquals(0f, features[2], tolerance)
    }

    @Test
    fun `middle MCP is exactly one unit from the origin after scaling`() {
        val hand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0f, 0f, 0f),
            middleMcp = NormalizedPoint(0.2f, 0.15f, 0.05f),
        )

        val features = HandFeatureExtractor.extractFeatures(hand)
        val dx = features[middleMcpIndex * 3]
        val dy = features[middleMcpIndex * 3 + 1]
        val dz = features[middleMcpIndex * 3 + 2]
        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)

        assertEquals(1f, magnitude, tolerance)
    }

    @Test
    fun `scaling the whole hand does not change the normalized features`() {
        val baseHand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0.1f, 0.1f, 0f),
            middleMcp = NormalizedPoint(0.3f, 0.1f, 0f),
        )
        val doubledHand = HandLandmarks(baseHand.points.map { NormalizedPoint(it.x * 2f, it.y * 2f, it.z * 2f) })

        val baseFeatures = HandFeatureExtractor.extractFeatures(baseHand)
        val doubledFeatures = HandFeatureExtractor.extractFeatures(doubledHand)

        baseFeatures.forEachIndexed { i, value ->
            assertEquals(value, doubledFeatures[i], tolerance)
        }
    }

    @Test
    fun `translating the whole hand does not change the normalized features`() {
        val baseHand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0.1f, 0.1f, 0f),
            middleMcp = NormalizedPoint(0.3f, 0.1f, 0f),
        )
        val shiftedHand = HandLandmarks(
            baseHand.points.map { NormalizedPoint(it.x + 0.5f, it.y - 0.2f, it.z + 0.1f) },
        )

        val baseFeatures = HandFeatureExtractor.extractFeatures(baseHand)
        val shiftedFeatures = HandFeatureExtractor.extractFeatures(shiftedHand)

        baseFeatures.forEachIndexed { i, value ->
            assertEquals(value, shiftedFeatures[i], tolerance)
        }
    }

    @Test
    fun `throws when wrist and middle MCP coincide, since scale would be zero`() {
        val hand = handWithWristAndMiddleMcp(
            wrist = NormalizedPoint(0.2f, 0.2f, 0f),
            middleMcp = NormalizedPoint(0.2f, 0.2f, 0f),
        )

        assertThrows<IllegalArgumentException> {
            HandFeatureExtractor.extractFeatures(hand)
        }
    }
}
