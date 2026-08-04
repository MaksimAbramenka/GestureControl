package com.gesturecontrol.core.ml.classifier

import android.content.Context
import com.gesturecontrol.domain.gesture.ClassifiedGesture
import com.gesturecontrol.domain.gesture.GestureClassifierOutput
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.TensorBuffer
import java.io.Closeable

/**
 * Wraps the trained gesture MLP (LiteRT CompiledModel) for on-device inference. The
 * CompiledModel/TensorBuffer instances are created once and reused across calls — per the
 * mediapipe-litert-pipeline skill's #1 perf pitfall, reconstructing the interpreter per frame
 * kills performance.
 */
class GestureClassifier(
    context: Context,
    modelAssetPath: String = DEFAULT_MODEL_ASSET_PATH,
) : Closeable {
    companion object {
        const val DEFAULT_MODEL_ASSET_PATH = "gesture_classifier.tflite"
    }

    private val model = CompiledModel.create(
        context.assets,
        modelAssetPath,
        CompiledModel.Options(Accelerator.CPU),
    )
    private val inputBuffers: List<TensorBuffer> = model.createInputBuffers()
    private val outputBuffers: List<TensorBuffer> = model.createOutputBuffers()

    fun classify(features: FloatArray): ClassifiedGesture {
        inputBuffers[0].writeFloat(features)
        model.run(inputBuffers, outputBuffers)
        val probabilities = outputBuffers[0].readFloat()
        return GestureClassifierOutput.interpret(probabilities)
    }

    override fun close() {
        inputBuffers.forEach { it.close() }
        outputBuffers.forEach { it.close() }
        model.close()
    }
}
