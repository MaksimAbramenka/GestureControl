package com.gesturecontrol.core.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/** Thin CameraX wrapper: binds a Preview + ImageAnalysis pair to a lifecycle. */
class CameraController(private val context: Context) {
    private val _surfaceRequests = MutableStateFlow<SurfaceRequest?>(null)
    val surfaceRequests: StateFlow<SurfaceRequest?> = _surfaceRequests.asStateFlow()
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    suspend fun bindToLifecycle(
        lifecycleOwner: LifecycleOwner,
        analyzer: ImageAnalysis.Analyzer,
        cameraSelector: CameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA,
    ) {
        val cameraProvider = ProcessCameraProvider.awaitInstance(context)

        val preview = Preview.Builder().build().apply {
            setSurfaceProvider { request -> _surfaceRequests.value = request }
        }

        val analysisResolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
            )
            .build()

        val imageAnalysis = ImageAnalysis
            .Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(analysisResolutionSelector)
            .build()
            .apply { setAnalyzer(analysisExecutor, analyzer) }

        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
    }
}
