package com.gesturecontrol.core.ml

import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.Handedness
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

object HandLandmarksMapper {
    fun toDomain(
        mediapipeHands: List<List<NormalizedLandmark>>,
        mediapipeHandedness: List<List<Category>>,
        timestampMs: Long,
        imageDimensions: ImageDimensions,
        fps: Float,
        mirrored: Boolean,
    ): HandDetectionResult {
        val hands = mediapipeHands.mapIndexed { index, hand ->
            HandLandmarks(
                points = hand.map { NormalizedPoint(x = it.x(), y = it.y(), z = it.z()) },
                handedness = parseHandedness(mediapipeHandedness.getOrNull(index), mirrored),
            )
        }
        return HandDetectionResult(
            hands = hands,
            timestampMs = timestampMs,
            imageDimensions = imageDimensions,
            fps = fps,
        )
    }

    private fun parseHandedness(
        categories: List<Category>?,
        mirrored: Boolean,
    ): Handedness? {
        val raw = when (categories?.firstOrNull()?.categoryName()) {
            "Left" -> Handedness.LEFT
            "Right" -> Handedness.RIGHT
            else -> null
        }
        return if (mirrored) {
            when (raw) {
                Handedness.LEFT -> Handedness.RIGHT
                Handedness.RIGHT -> Handedness.LEFT
                null -> null
            }
        } else {
            raw
        }
    }
}
