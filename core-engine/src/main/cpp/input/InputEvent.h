#pragma once

#include <cstdint>

namespace gesture_canvas {

struct InputEvent {
    enum class State : int32_t {
        IDLE = 0,
        HOVER = 1,
        DRAW_START = 2,
        DRAW_MOVE = 3,
        DRAW_END = 4,
        ERASE = 5,
    };

    float x;
    float y;
    State state;
    float pressure;
    int64_t timestamp_ms;
};

}  // namespace gesture_canvas
