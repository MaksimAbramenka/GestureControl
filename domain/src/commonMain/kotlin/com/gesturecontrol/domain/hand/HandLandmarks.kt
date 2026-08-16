package com.gesturecontrol.domain.hand

enum class Handedness {
    LEFT,
    RIGHT,
}

data class NormalizedPoint(
    val x: Float,
    val y: Float,
    val z: Float,
)

data class HandLandmarks(
    val points: List<NormalizedPoint>,
    val handedness: Handedness? = null,
) {
    companion object {
        const val LANDMARK_COUNT = 21
        private const val INDEX_FINGERTIP_INDEX = 8
    }

    init {
        require(points.size == LANDMARK_COUNT) {
            "A hand must have exactly $LANDMARK_COUNT landmarks, got ${points.size}"
        }
    }

    val indexFingertip: NormalizedPoint
        get() = points[INDEX_FINGERTIP_INDEX]
}

data class HandDetectionResult(
    val hands: List<HandLandmarks>,
    val timestampMs: Long,
    val imageDimensions: ImageDimensions,
    val fps: Float,
)
