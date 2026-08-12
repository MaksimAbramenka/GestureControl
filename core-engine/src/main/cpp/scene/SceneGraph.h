#pragma once

#include <optional>
#include <vector>

#include "input/InputEvent.h"
#include "input/PointSmoother.h"
#include "scene/Stroke.h"

namespace gesture_canvas {

// Owns the drawing state: brush settings and the strokes built from submitted InputEvents. Pure
// C++, no Android/JNI dependency -- testable with GoogleTest independent of the render/JNI boundary.
    class SceneGraph {
    public:
        void setBrushColor(float r, float g, float b);

        void setBrushSize(float size);

        void submitInput(const InputEvent &event);

        void clear();

        const std::vector<Stroke> &strokes() const { return strokes_; }

        // Finalized strokes plus the in-progress stroke (if any), for rendering -- so drawing
        // renders live as the gesture moves rather than only appearing after DRAW_END.
        std::vector<Stroke> visibleStrokes() const;

    private:
        void beginStroke(float x, float y);

        void extendStroke(float x, float y);

        void endStroke();

        void eraseNear(float x, float y);

        std::vector<Stroke> strokes_;
        std::optional<Stroke> currentStroke_;
        PointSmoother smoother_;

        float brushR_ = 0.1f;
        float brushG_ = 0.9f;
        float brushB_ = 1.0f;
        float brushSize_ = 0.015f;

        static constexpr float kEraseRadius = 0.05f;
    };

} // namespace gesture_canvas
