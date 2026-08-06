#pragma once

#include <vector>

#include "scene/Stroke.h"

namespace gesture_canvas {

// Tessellates and draws strokes as GLES3 triangle-strip ribbons. Assumes a current EGL context;
// GL-coupled, so verified on-device rather than unit tested -- see RibbonTessellator for the
// pure geometry that IS unit tested.
    class StrokeRenderer {
    public:
        bool init();

        void resize(int width, int height);

        void draw(const std::vector<Stroke> &strokes);

    private:
        unsigned int program_ = 0;
        unsigned int vao_ = 0;
        unsigned int vbo_ = 0;
        int colorUniformLocation_ = -1;
    };

} // namespace gesture_canvas
