#pragma once

namespace gesture_canvas {

class TriangleRenderer {
public:
    bool init();
    void resize(int width, int height);
    void draw();

private:
    unsigned int program_ = 0;
    unsigned int vao_ = 0;
    unsigned int vbo_ = 0;
};

} // namespace gesture_canvas
