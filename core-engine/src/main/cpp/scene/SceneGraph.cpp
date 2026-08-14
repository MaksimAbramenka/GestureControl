#include "scene/SceneGraph.h"

#include <cmath>

namespace gesture_canvas {

    void SceneGraph::setBrushColor(float r, float g, float b) {
        brushR_ = r;
        brushG_ = g;
        brushB_ = b;
    }

    void SceneGraph::setBrushSize(float size) {
        brushSize_ = size;
    }

    void SceneGraph::submitInput(const InputEvent &event) {
        float timestampSeconds = static_cast<float>(event.timestamp_ms) / 1000.0f;

        if (event.state != InputEvent::State::ERASE) {
            wasErasing_ = false;
        }

        switch (event.state) {
            case InputEvent::State::DRAW_START: {
                if (currentStroke_.has_value()) {
                    finalizeCurrentStroke();
                }
                smoother_.reset();
                Point2D point = smoother_.smooth(event.x, event.y, timestampSeconds);

                if (shouldContinuePreviousStroke(event.x, event.y, event.timestamp_ms)) {
                    resumePreviousStroke(point.x, point.y);
                } else {
                    pushUndoSnapshot();
                    beginStroke(point.x, point.y);
                }
                break;
            }
            case InputEvent::State::DRAW_MOVE: {
                if (currentStroke_.has_value()) {
                    Point2D point = smoother_.smooth(event.x, event.y, timestampSeconds);
                    extendStroke(point.x, point.y);
                } else {
                    pushUndoSnapshot();
                    smoother_.reset();
                    Point2D point = smoother_.smooth(event.x, event.y, timestampSeconds);
                    beginStroke(point.x, point.y);
                }
                break;
            }
            case InputEvent::State::DRAW_END: {
                if (currentStroke_.has_value()) {
                    Point2D point = smoother_.smooth(event.x, event.y, timestampSeconds);
                    extendStroke(point.x, point.y);
                    endStroke(event.timestamp_ms, event.x, event.y);
                }
                break;
            }
            case InputEvent::State::ERASE:
                if (!wasErasing_) {
                    pushUndoSnapshot();
                    wasErasing_ = true;
                }
                lastEndedPoint_.reset();
                eraseNear(event.x, event.y);
                break;
            case InputEvent::State::IDLE:
            case InputEvent::State::HOVER:
                break;
        }
    }

    void SceneGraph::clear() {
        strokes_.clear();
        currentStroke_.reset();
        smoother_.reset();
        lastEndedPoint_.reset();
        undoStack_.clear();
        redoStack_.clear();
    }

    void SceneGraph::undo() {
        if (undoStack_.empty()) {
            return;
        }
        redoStack_.push_back(strokes_);
        strokes_ = std::move(undoStack_.back());
        undoStack_.pop_back();
        currentStroke_.reset();
        lastEndedPoint_.reset();
    }

    void SceneGraph::redo() {
        if (redoStack_.empty()) {
            return;
        }
        undoStack_.push_back(strokes_);
        strokes_ = std::move(redoStack_.back());
        redoStack_.pop_back();
        currentStroke_.reset();
        lastEndedPoint_.reset();
    }

    void SceneGraph::pushUndoSnapshot() {
        undoStack_.push_back(strokes_);
        redoStack_.clear();
    }

    std::vector<Stroke> SceneGraph::visibleStrokes() const {
        std::vector<Stroke> result = strokes_;
        if (currentStroke_.has_value()) {
            result.push_back(*currentStroke_);
        }
        return result;
    }

    void SceneGraph::beginStroke(float x, float y) {
        Stroke stroke;
        stroke.r = brushR_;
        stroke.g = brushG_;
        stroke.b = brushB_;
        stroke.width = brushSize_;
        stroke.points.push_back({x, y});
        currentStroke_ = stroke;
    }

    void SceneGraph::extendStroke(float x, float y) {
        currentStroke_->points.push_back({x, y});
    }

    void SceneGraph::finalizeCurrentStroke() {
        strokes_.push_back(*currentStroke_);
        currentStroke_.reset();
    }

    void SceneGraph::endStroke(int64_t timestampMs, float rawX, float rawY) {
        finalizeCurrentStroke();
        lastEndedPoint_ = Point2D{rawX, rawY};
        lastEndedTimestampMs_ = timestampMs;
    }

    bool SceneGraph::shouldContinuePreviousStroke(float x, float y, int64_t timestampMs) const {
        if (!lastEndedPoint_.has_value() || strokes_.empty()) {
            return false;
        }
        if (timestampMs - lastEndedTimestampMs_ > kStrokeContinuityWindowMs) {
            return false;
        }
        float dx = x - lastEndedPoint_->x;
        float dy = y - lastEndedPoint_->y;
        return std::sqrt(dx * dx + dy * dy) <= kStrokeContinuityRadius;
    }

    void SceneGraph::resumePreviousStroke(float x, float y) {
        currentStroke_ = strokes_.back();
        strokes_.pop_back();
        currentStroke_->points.push_back({x, y});
    }

    void SceneGraph::eraseNear(float x, float y) {
        std::vector<Stroke> result;
        result.reserve(strokes_.size());

        for (const auto &stroke: strokes_) {
            std::vector<Point2D> run;

            auto flushRun = [&]() {
                if (run.size() >= 2) {
                    Stroke piece = stroke;
                    piece.points = std::move(run);
                    result.push_back(std::move(piece));
                }
                run.clear();
            };

            for (const auto &point: stroke.points) {
                float dx = point.x - x;
                float dy = point.y - y;
                bool withinEraser = std::sqrt(dx * dx + dy * dy) <= kEraseRadius;
                if (withinEraser) {
                    flushRun();
                } else {
                    run.push_back(point);
                }
            }
            flushRun();
        }

        strokes_ = std::move(result);
    }

} // namespace gesture_canvas
