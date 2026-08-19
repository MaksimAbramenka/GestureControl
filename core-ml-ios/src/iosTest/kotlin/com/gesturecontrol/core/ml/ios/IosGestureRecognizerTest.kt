package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPCategory
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.core.ml.ios.mediapipe.MPPLandmark
import com.gesturecontrol.core.ml.ios.mediapipe.MPPNormalizedLandmark
import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class)
class IosGestureRecognizerTest {
    @Test
    fun `classifies the first hand when landmarks are present`() {
        val landmarks = List(21) { index ->
            MPPNormalizedLandmark(
                x = 0.5f + index * 0.01f,
                y = 0.5f + index * 0.01f,
                z = 0f,
                visibility = null,
                presence = null,
            )
        }
        val handedness = MPPCategory(index = 0, score = 0.9f, categoryName = "Right", displayName = "Right")
        val result = MPPHandLandmarkerResult(
            landmarks = listOf(landmarks),
            worldLandmarks = listOf<List<MPPLandmark>>(),
            handedness = listOf(listOf(handedness)),
            timestampInMilliseconds = 0L,
        )

        val classified = IosGestureRecognizer.classifyFirstHand(result)

        assertNotNull(classified, "expected a classification when a hand is present")
        assertTrue(classified.confidence in 0f..1f, "confidence ${classified.confidence} out of [0,1]")
    }

    @Test
    fun `returns null when no hand was detected`() {
        val result = MPPHandLandmarkerResult(
            landmarks = listOf<List<MPPNormalizedLandmark>>(),
            worldLandmarks = listOf<List<MPPLandmark>>(),
            handedness = listOf<List<MPPCategory>>(),
            timestampInMilliseconds = 0L,
        )

        assertNull(IosGestureRecognizer.classifyFirstHand(result))
    }
}
