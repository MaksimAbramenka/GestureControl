package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPBaseOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarker
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPImage
import com.gesturecontrol.core.ml.ios.mediapipe.MPPRunningMode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIRectFill
import kotlin.test.Test
import kotlin.test.assertNotNull

// Proves the real MediaPipe iOS stack (Objective-C headers, force-loaded C++ graph runtime,
// TFLite/XNNPACK) is actually reachable and runnable from Kotlin/Native, ahead of a real camera
// feed in a later stage. IMAGE running mode (a single static frame) is exactly what's testable
// headlessly on the Simulator -- no camera hardware involved.
@OptIn(ExperimentalForeignApi::class)
class HandLandmarkerBridgeTest {
    @Test
    fun `HandLandmarker loads the model and runs detection on a blank image`() {
        val baseOptions = MPPBaseOptions()
        baseOptions.modelAssetPath = TEST_HAND_LANDMARKER_MODEL_PATH

        val options = MPPHandLandmarkerOptions()
        options.baseOptions = baseOptions
        options.runningMode = MPPRunningMode.MPPRunningModeImage
        options.numHands = 2L

        val landmarker = MPPHandLandmarker(options = options, error = null)
        assertNotNull(landmarker, "HandLandmarker failed to initialize from $TEST_HAND_LANDMARKER_MODEL_PATH")

        UIGraphicsBeginImageContext(CGSizeMake(64.0, 64.0))
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, 64.0, 64.0))
        val blankImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        assertNotNull(blankImage, "failed to render the blank test image")

        val mpImage = MPPImage(uIImage = blankImage, error = null)
        assertNotNull(mpImage, "failed to wrap the blank UIImage as an MPImage")

        val result = landmarker.detectImage(mpImage, error = null)
        assertNotNull(result, "detectImage returned null -- inference did not complete")
    }
}
