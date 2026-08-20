package com.gesturecontrol.core.engine.ios

import com.gesturecontrol.domain.gesture.InputState

/** Mirrors gesture_canvas::InputEvent::State's ordinals (GestureCanvasBridge.h) and Android's own
 * core-engine InputEventState -- duplicated here rather than shared, since core-engine is a
 * JVM-only (JNI) module and not something worth a cross-module abstraction for six constants. */
fun InputState.toNativeState(): Int = when (this) {
    InputState.IDLE -> 0
    InputState.HOVER -> 1
    InputState.DRAW_START -> 2
    InputState.DRAW_MOVE -> 3
    InputState.DRAW_END -> 4
    InputState.ERASE -> 5
}
