package com.gesturecontrol.core.ml.training

import android.content.Context
import com.gesturecontrol.domain.gesture.GestureClass
import com.gesturecontrol.domain.hand.Handedness
import com.gesturecontrol.domain.training.RecordingProgress
import java.io.File
import java.util.concurrent.Executors

class RecordingProgressStore(context: Context, fileName: String = DEFAULT_FILE_NAME) {
    companion object {
        const val DEFAULT_FILE_NAME = "recording_progress.csv"
    }

    private val file = File(context.getExternalFilesDir(null), fileName)
    private val writeExecutor = Executors.newSingleThreadExecutor()

    fun load(): RecordingProgress {
        if (!file.exists()) return RecordingProgress()

        var progress = RecordingProgress()
        file.readLines().forEach { line ->
            val parts = line.split(",")
            val gestureClass = parts.getOrNull(0)?.let { runCatching { GestureClass.valueOf(it) }.getOrNull() }
            val handedness = parts.getOrNull(1)?.let { runCatching { Handedness.valueOf(it) }.getOrNull() }
            val count = parts.getOrNull(2)?.toIntOrNull()
            if (gestureClass != null && handedness != null && count != null) {
                progress = progress.withCount(gestureClass, handedness, count)
            }
        }
        return progress
    }

    fun save(progress: RecordingProgress) {
        writeExecutor.execute {
            val lines = GestureClass.entries.flatMap { gestureClass ->
                Handedness.entries.map { handedness ->
                    "${gestureClass.name},${handedness.name},${progress.count(gestureClass, handedness)}"
                }
            }
            file.writeText(lines.joinToString("\n"))
        }
    }
}
