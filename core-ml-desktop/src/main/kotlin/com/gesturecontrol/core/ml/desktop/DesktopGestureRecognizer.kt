package com.gesturecontrol.core.ml.desktop

import com.gesturecontrol.domain.gesture.ClassifiedGesture
import com.gesturecontrol.domain.gesture.GestureClassifierOutput
import com.gesturecontrol.domain.gesture.GestureMlp
import com.gesturecontrol.domain.gesture.HandFeatureExtractor
import com.gesturecontrol.domain.hand.HandLandmarks

/**
 * Mirrors `IosGestureRecognizer` almost exactly, minus the "parse a raw platform result into
 * HandLandmarks" step it needs and this doesn't -- `SidecarFrame.toDomain()` already did that.
 * Genuinely thin wiring, not new logic: [HandFeatureExtractor] and [GestureMlp] are the same
 * shared `domain` code Android and iOS both already use unchanged.
 */
object DesktopGestureRecognizer {
    fun recognize(landmarks: HandLandmarks): ClassifiedGesture {
        val features = HandFeatureExtractor.extractFeatures(landmarks)
        val probabilities = GestureMlp.run(features)
        return GestureClassifierOutput.interpret(probabilities)
    }
}
