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
        switch (event.state) {
            case InputEvent::State::DRAW_START:
                if (currentStroke_.has_value()) {
                    endStroke();
                }
                beginStroke(event.x, event.y);
                break;
            case InputEvent::State::DRAW_MOVE:
                if (currentStroke_.has_value()) {
                    extendStroke(event.x, event.y);
                } else {
                    beginStroke(event.x, event.y);
                }
                break;
            case InputEvent::State::DRAW_END:
                if (currentStroke_.has_value()) {
                    extendStroke(event.x, event.y);
                    endStroke();
                }
                break;
            case InputEvent::State::ERASE:
                eraseNear(event.x, event.y);
                break;
            case InputEvent::State::IDLE:
            case InputEvent::State::HOVER:
                break;
        }
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

    void SceneGraph::endStroke() {
        strokes_.push_back(*currentStroke_);
        currentStroke_.reset();
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
