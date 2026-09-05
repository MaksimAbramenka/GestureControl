package com.gesturecontrol.core.ml.desktop

import com.gesturecontrol.domain.hand.HandLandmarks
import com.gesturecontrol.domain.hand.Handedness
import com.gesturecontrol.domain.hand.ImageDimensions
import com.gesturecontrol.domain.hand.NormalizedPoint
import kotlinx.serialization.Serializable

/**
 * Mirrors hand_tracking_sidecar.py's own documented JSON-line output contract exactly --
 * `landmarks`/`handedness` are null together (no hand that frame), never partially null. Kept as
 * a private wire format rather than reusing `HandLandmarks` directly since the sidecar always
 * reports frame dimensions (needed for viewport mapping downstream) whereas `HandLandmarks` has no
 * such concept, and `HandLandmarks` itself rejects anything but exactly 21 points -- easier to
 * validate that shape once, in [toDomain], than to make the domain type permissive.
 */
@Serializable
internal data class SidecarFrame(
    val ts: Long,
    val width: Int,
    val height: Int,
    val handedness: String? = null,
    val landmarks: List<List<Float>>? = null,
) {
    /** Null if this frame reported no hand, or reported a shape [HandLandmarks] itself rejects
     * (a malformed sidecar frame is exactly as valid a reason to skip a frame as no hand at all --
     * neither should crash the pipeline over one bad line). */
    fun toDomain(): HandLandmarks? {
        val points = landmarks ?: return null
        if (points.size != HandLandmarks.LANDMARK_COUNT) return null
        if (points.any { it.size != 3 }) return null

        return HandLandmarks(
            points = points.map { NormalizedPoint(x = it[0], y = it[1], z = it[2]) },
            handedness = when (handedness) {
                "Left" -> Handedness.LEFT
                "Right" -> Handedness.RIGHT
                else -> null
            },
        )
    }

    fun imageDimensions(): ImageDimensions = ImageDimensions(width = width, height = height)
}
