package com.gesturecontrol.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.graphics.createBitmap
import com.gesturecontrol.domain.hand.HandDetectionResult
import com.gesturecontrol.domain.hand.ImageDimensions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.Closeable

/**
 * Wraps MediaPipe's HandLandmarker (LIVE_STREAM mode) as a CameraX [ImageAnalysis.Analyzer].
 */
class HandLandmarkerAnalyzer(
    context: Context,
    private val isFrontCamera: Boolean = true,
    modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
    numHands: Int = DEFAULT_NUM_HANDS,
) : ImageAnalysis.Analyzer,
    Closeable {
    companion object {
        const val DEFAULT_MODEL_ASSET_PATH = "hand_landmarker.task"
        const val DEFAULT_NUM_HANDS = 2
        private const val TAG = "HandLandmarkerAnalyzer"
    }

    private val _results = MutableSharedFlow<HandDetectionResult>(replay = 1, extraBufferCapacity = 1)
    val results: SharedFlow<HandDetectionResult> = _results.asSharedFlow()

    @Volatile
    private var latestImageDimensions = ImageDimensions(width = 1, height = 1)

    private val handLandmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions
            .builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelAssetPath).build())
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(numHands)
            .setResultListener { result, _ ->
                _results.tryEmit(
                    HandLandmarksMapper.toDomain(
                        mediapipeHands = result.landmarks(),
                        timestampMs = result.timestampMs(),
                        imageDimensions = latestImageDimensions,
                    ),
                )
            }.setErrorListener { error -> Log.e(TAG, "HandLandmarker error", error) }
            .build(),
    )

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        val bitmapBuffer = createBitmap(imageProxy.width, imageProxy.height)
        bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            if (isFrontCamera) {
                postScale(-1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat())
            }
        }
        val rotatedBitmap =
            Bitmap.createBitmap(bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true)

        latestImageDimensions = ImageDimensions(width = rotatedBitmap.width, height = rotatedBitmap.height)

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        handLandmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
        imageProxy.close()
    }

    override fun close() {
        handLandmarker.close()
    }
}
