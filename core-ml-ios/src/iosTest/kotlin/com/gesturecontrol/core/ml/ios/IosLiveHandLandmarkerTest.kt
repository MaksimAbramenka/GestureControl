package com.gesturecontrol.core.ml.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertTrue

// LIVE_STREAM mode is what the real camera pipeline needs, but sending it a real frame and
// waiting for the async delegate callback needs a real camera/run loop pump that isn't available
// headlessly. What IS provable on the Simulator without a camera: that MPPHandLandmarker actually
// accepts LIVE_STREAM mode plus a delegate conforming via Kotlin/Native's Objective-C protocol
// interop, and constructs successfully -- the same "is the real stack reachable" bar
// HandLandmarkerBridgeTest already established for IMAGE mode.
@OptIn(ExperimentalForeignApi::class)
class IosLiveHandLandmarkerTest {
    @Test
    fun `constructs successfully in live stream mode with a valid model`() {
        val landmarker = IosLiveHandLandmarker(TEST_HAND_LANDMARKER_MODEL_PATH) { _, _ -> }
        assertTrue(landmarker.isReady, "LIVE_STREAM HandLandmarker failed to initialize")
    }
}
