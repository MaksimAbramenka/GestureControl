package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.domain.gesture.ClassifiedGesture
import com.gesturecontrol.domain.gesture.GestureClassifierOutput
import com.gesturecontrol.domain.gesture.GestureMlp
import com.gesturecontrol.domain.gesture.HandFeatureExtractor
import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.Handedness
import com.gesturecontrol.domain.hand.NormalizedPoint
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
object IosGestureRecognizer {
    fun classifyFirstHand(result: MPPHandLandmarkerResult): ClassifiedGesture? {
        val landmarks = toHandLandmarks(result) ?: return null
        val features = HandFeatureExtractor.extractFeatures(landmarks)
        val probabilities = GestureMlp.run(features)
        return GestureClassifierOutput.interpret(probabilities)
    }

    private fun toHandLandmarks(result: MPPHandLandmarkerResult): HandLandmarks? {
        val firstHandLandmarks = result.landmarks.firstOrNull() as? List<*> ?: return null
        if (firstHandLandmarks.size != HandLandmarks.LANDMARK_COUNT) return null

        val points = firstHandLandmarks.map { landmark ->
            val normalized = landmark as com.gesturecontrol.core.ml.ios.mediapipe.MPPNormalizedLandmark
            NormalizedPoint(x = normalized.x, y = normalized.y, z = normalized.z)
        }

        val handednessLabel = (result.handedness.firstOrNull() as? List<*>)
            ?.firstOrNull() as? com.gesturecontrol.core.ml.ios.mediapipe.MPPCategory
        val handedness = when (handednessLabel?.categoryName) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> null
        }

        return HandLandmarks(points = points, handedness = handedness)
    }
}
