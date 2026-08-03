package com.gesturecontrol.domain.hand

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class HandLandmarks(
    val points: List<NormalizedPoint>,
) {
    companion object {
        const val LANDMARK_COUNT = 21
    }

    init {
        require(points.size == LANDMARK_COUNT) {
            "A hand must have exactly $LANDMARK_COUNT landmarks, got ${points.size}"
        }
    }
}

data class HandDetectionResult(
    val hands: List<HandLandmarks>,
    val timestampMs: Long,
    val imageDimensions: ImageDimensions,
)
