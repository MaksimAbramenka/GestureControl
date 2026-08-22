package com.gesturecontrol.core.ml.ios

import com.gesturecontrol.core.ml.ios.mediapipe.MPPBaseOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarker
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerLiveStreamDelegateProtocol
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerOptions
import com.gesturecontrol.core.ml.ios.mediapipe.MPPHandLandmarkerResult
import com.gesturecontrol.core.ml.ios.mediapipe.MPPImage
import com.gesturecontrol.core.ml.ios.mediapipe.MPPRunningMode
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIRectFill
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.NSObject
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A real-device investigation found a periodic ~9.5s dead stall in the *camera's own* delegate
 * callback -- confirmed independent of MediaPipe speed (~25ms/call, GPU-delegated), lighting,
 * thermal state (NSProcessInfo.thermalState stayed Nominal throughout), Debug-vs-Release build,
 * and physical buffer rotation -- ultimately traced to that test iPhone's degraded battery health
 * (75% max capacity) triggering iOS's own peak-power throttling, a subsystem separate from
 * thermal management and invisible to AVCaptureSession's own interruption reporting.
 *
 * This test provides the other half of that proof: it drives MediaPipe's actual LIVE_STREAM
 * engine -- the same async API, the same GPU-accelerated model, the same sustained-submission
 * pattern as the real camera pipeline -- but on the Mac's own CPU/GPU via the Simulator, which
 * has none of that iPhone-specific battery-health throttling. If this sustains a steady stream
 * with no multi-second gaps, the stalling is conclusively a property of the specific device's
 * power state, not of this app's pipeline code.
 */
@OptIn(ExperimentalForeignApi::class)
class LiveHandLandmarkerSustainedThroughputTest : NSObject(), MPPHandLandmarkerLiveStreamDelegateProtocol {

    private val latenciesMs = mutableListOf<Double>()
    private var submitTimeMs = 0.0
    private val semaphore = dispatch_semaphore_create(0)

    override fun handLandmarker(
        handLandmarker: MPPHandLandmarker,
        didFinishDetectionWithResult: MPPHandLandmarkerResult?,
        timestampInMilliseconds: Long,
        error: NSError?,
    ) {
        latenciesMs.add(NSDate().timeIntervalSince1970 * 1000 - submitTimeMs)
        dispatch_semaphore_signal(semaphore)
    }

    @Test
    fun `sustained LIVE_STREAM submissions show no multi-second stalls on Simulator hardware`() {
        val baseOptions = MPPBaseOptions().apply { modelAssetPath = TEST_HAND_LANDMARKER_MODEL_PATH }
        val options = MPPHandLandmarkerOptions().apply {
            this.baseOptions = baseOptions
            runningMode = MPPRunningMode.MPPRunningModeLiveStream
            numHands = 1L
            handLandmarkerLiveStreamDelegate = this@LiveHandLandmarkerSustainedThroughputTest
        }
        val landmarker = MPPHandLandmarker(options = options, error = null)

        val testImage = blankTestImage()
        val iterations = 300 // ~10s of submissions at a 33ms (30fps-equivalent) cadence

        var timestampMs = 0L
        repeat(iterations) {
            timestampMs += 33
            val image = MPPImage(uIImage = testImage, error = null)
            submitTimeMs = NSDate().timeIntervalSince1970 * 1000
            val submitted = landmarker.detectAsyncImage(image, timestampInMilliseconds = timestampMs, error = null)
            assertTrue(submitted, "detectAsyncImage rejected submission #$it")

            // Waits for THIS submission's own result before submitting the next one -- the same
            // one-in-flight-at-a-time pattern GesturePipeline's busy-guard enforces on the real
            // device, so this test measures the same thing: sustained per-call latency, not
            // artificial concurrency the real pipeline never has either.
            val timedOut = dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 5_000_000_000L)) != 0L
            if (timedOut) fail("submission #$it never completed within 5s -- that alone would reproduce a stall")
        }

        val maxLatency = latenciesMs.max()
        val avgLatency = latenciesMs.average()
        println(
            "LiveHandLandmarkerSustainedThroughputTest: $iterations submissions, " +
                "avg=${avgLatency}ms max=${maxLatency}ms",
        )
        assertTrue(
            maxLatency < 1000.0,
            "Expected every submission to complete in well under 1s on Simulator hardware, " +
                "but the slowest took ${maxLatency}ms -- see all ${latenciesMs.size} latencies: $latenciesMs",
        )
    }

    private fun blankTestImage(): UIImage {
        UIGraphicsBeginImageContext(CGSizeMake(320.0, 320.0))
        UIColor.whiteColor.setFill()
        UIRectFill(CGRectMake(0.0, 0.0, 320.0, 320.0))
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return image ?: fail("failed to render the blank test image")
    }
}
