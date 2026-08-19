package com.gesturecontrol.iosshared

/** Entry point exposed to the Swift host app. Placeholder for Stage 6a -- proves the Kotlin/Native
 * framework actually builds, embeds, and is callable from Swift before any real UI/pipeline code
 * depends on it. */
object GestureControlKit {
    fun statusMessage(): String = "GestureControlKit loaded"
}
