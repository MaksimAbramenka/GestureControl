package com.gesturecontrol.core.engine.desktop

import com.gesturecontrol.domain.gesture.InputState

/** Mirrors gesture_canvas::InputEvent::State's ordinals (see DesktopRendererBridge.cpp) and
 * Android/iOS's own equivalents -- duplicated here rather than shared, same reasoning
 * core-engine-ios's own InputStateMapping.kt already gives: not worth a cross-module abstraction
 * for six constants. */
fun InputState.toNativeState(): Int = when (this) {
    InputState.IDLE -> 0
    InputState.HOVER -> 1
    InputState.DRAW_START -> 2
    InputState.DRAW_MOVE -> 3
    InputState.DRAW_END -> 4
    InputState.ERASE -> 5
}
