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

        void undo();

        void redo();

        bool canUndo() const { return !undoStack_.empty(); }

        bool canRedo() const { return !redoStack_.empty(); }

        const std::vector<Stroke> &strokes() const { return strokes_; }

        // Finalized strokes plus the in-progress stroke (if any), for rendering -- so drawing
        // renders live as the gesture moves rather than only appearing after DRAW_END.
        std::vector<Stroke> visibleStrokes() const;

    private:
        void beginStroke(float x, float y);

        void extendStroke(float x, float y);

        void finalizeCurrentStroke();

        void endStroke(int64_t timestampMs, float rawX, float rawY);

        void eraseNear(float x, float y);

        void pushUndoSnapshot();

        bool shouldContinuePreviousStroke(float x, float y, int64_t timestampMs) const;

        void resumePreviousStroke(float x, float y);

        std::vector<Stroke> strokes_;
        std::optional<Stroke> currentStroke_;
        PointSmoother smoother_;

        std::vector<std::vector<Stroke>> undoStack_;
        std::vector<std::vector<Stroke>> redoStack_;
        bool wasErasing_ = false;

        std::optional<Point2D> lastEndedPoint_;
        int64_t lastEndedTimestampMs_ = 0;

        float brushR_ = 0.1f;
        float brushG_ = 0.9f;
        float brushB_ = 1.0f;
        float brushSize_ = 0.015f;

        static constexpr float kEraseRadius = 0.05f;
        static constexpr float kStrokeContinuityRadius = 0.03f;
        static constexpr int64_t kStrokeContinuityWindowMs = 400;
    };

} // namespace gesture_canvas
