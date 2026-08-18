package com.gesturecontrol.core.camera.ios

import kotlinx.cinterop.ExperimentalForeignApi
import kotlin.test.Test
import kotlin.test.assertFalse

// The Simulator has no real camera hardware, so `configure()` is expected to return false here --
// this test's job is to prove that path is handled gracefully (no crash, isConfigured stays
// false) rather than to prove frames actually flow, which needs a physical device.
@OptIn(ExperimentalForeignApi::class)
class IosCameraCaptureTest {
    @Test
    fun `configure without a real camera fails gracefully rather than crashing`() {
        val capture = IosCameraCapture(onFrame = {})

        val configured = capture.configure()

        assertFalse(configured)
        assertFalse(capture.isConfigured)
    }
}
