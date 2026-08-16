package com.gesturecontrol.domain.gesture

import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.NormalizedPoint
import kotlin.math.sqrt

object HandFeatureExtractor {
    const val FEATURE_VECTOR_SIZE = HandLandmarks.LANDMARK_COUNT * 3

    private const val WRIST_INDEX = 0
    private const val MIDDLE_MCP_INDEX = 9

    fun extractFeatures(hand: HandLandmarks): FloatArray {
        val wrist = hand.points[WRIST_INDEX]
        val middleMcp = hand.points[MIDDLE_MCP_INDEX]
        val scale = distance(wrist, middleMcp)
        require(scale > 0f) {
            "Wrist and middle-MCP landmarks coincide (scale would be zero): $wrist"
        }

        val features = FloatArray(FEATURE_VECTOR_SIZE)
        hand.points.forEachIndexed { index, point ->
            features[index * 3] = (point.x - wrist.x) / scale
            features[index * 3 + 1] = (point.y - wrist.y) / scale
            features[index * 3 + 2] = (point.z - wrist.z) / scale
        }
        return features
    }

    private fun distance(a: NormalizedPoint, b: NormalizedPoint): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        val dz = a.z - b.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}
