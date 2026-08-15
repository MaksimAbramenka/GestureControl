package com.gesturecontrol.core.voice

import android.content.Context
import java.io.File

object VoiceModelConfig {
    const val MODEL_FILE_NAME = "functiongemma-270m-mobile-actions.litertlm"

    fun modelFile(context: Context): File =
        File(context.getExternalFilesDir(null), "models/$MODEL_FILE_NAME")
}
