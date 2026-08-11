package com.gesturecontrol.core.ml.training

import android.content.Context
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.gesture.HandFeatureExtractor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class TrainingDataRecorder(context: Context, fileName: String = DEFAULT_FILE_NAME) {
    companion object {
        const val DEFAULT_FILE_NAME = "training_data.csv"
    }

    private val file = File(context.getExternalFilesDir(null), fileName)
    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val rowCount = AtomicInteger(0)

    val recordedRowCount: Int get() = rowCount.get()

    init {
        if (!file.exists()) {
            file.writeText(TrainingDataCsvFormatter.header(HandFeatureExtractor.FEATURE_VECTOR_SIZE) + "\n")
        } else {
            rowCount.set((file.readLines().size - 1).coerceAtLeast(0))
        }
    }

    fun record(gestureClass: GestureClass, features: FloatArray) {
        writeExecutor.execute {
            file.appendText(TrainingDataCsvFormatter.row(gestureClass, features) + "\n")
            rowCount.incrementAndGet()
        }
    }

    fun clear() {
        writeExecutor.execute {
            file.writeText(TrainingDataCsvFormatter.header(HandFeatureExtractor.FEATURE_VECTOR_SIZE) + "\n")
            rowCount.set(0)
        }
    }
}
