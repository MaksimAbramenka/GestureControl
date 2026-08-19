package com.gesturecontrol.domain.gesture

import kotlin.math.exp

object GestureMlp {
    fun run(features: FloatArray): FloatArray {
        require(features.size == GestureMlpWeights.INPUT_SIZE) {
            "Expected ${GestureMlpWeights.INPUT_SIZE} input features, got ${features.size}"
        }

        val hidden1 = denseRelu(features, GestureMlpWeights.W1, GestureMlpWeights.B1, GestureMlpWeights.HIDDEN1_SIZE)
        val hidden2 = denseRelu(hidden1, GestureMlpWeights.W2, GestureMlpWeights.B2, GestureMlpWeights.HIDDEN2_SIZE)
        val logits = dense(hidden2, GestureMlpWeights.W3, GestureMlpWeights.B3, GestureMlpWeights.OUTPUT_SIZE)
        return softmax(logits)
    }

    private fun dense(input: FloatArray, weights: FloatArray, bias: FloatArray, outputSize: Int): FloatArray {
        val inputSize = input.size
        val output = FloatArray(outputSize)
        for (o in 0 until outputSize) {
            var sum = bias[o]
            val rowOffset = o * inputSize
            for (i in 0 until inputSize) {
                sum += input[i] * weights[rowOffset + i]
            }
            output[o] = sum
        }
        return output
    }

    private fun denseRelu(input: FloatArray, weights: FloatArray, bias: FloatArray, outputSize: Int): FloatArray {
        val output = dense(input, weights, bias, outputSize)
        for (i in output.indices) {
            if (output[i] < 0f) output[i] = 0f
        }
        return output
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.max()
        val exponentials = FloatArray(logits.size) { exp(logits[it] - maxLogit) }
        val sum = exponentials.sum()
        return FloatArray(logits.size) { exponentials[it] / sum }
    }
}
