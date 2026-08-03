package com.gesturecontrol.core.ml

import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object HandLandmarksMapper {
    fun toDomain(
        mediapipeHands: List<List<NormalizedLandmark>>,
        timestampMs: Long,
        imageDimensions: ImageDimensions,
        fps: Float,
    ): HandDetectionResult {
        val hands = mediapipeHands.map { hand ->
            HandLandmarks(hand.map { NormalizedPoint(x = it.x(), y = it.y(), z = it.z()) })
        }
        return HandDetectionResult(
            hands = hands,
            timestampMs = timestampMs,
            imageDimensions = imageDimensions,
            fps = fps,
        )
    }
}
