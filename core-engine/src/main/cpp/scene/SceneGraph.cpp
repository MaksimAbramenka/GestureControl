#include "scene/SceneGraph.h"

#include <algorithm>
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
        strokes_.erase(
                std::remove_if(
                        strokes_.begin(), strokes_.end(),
                        [x, y](const Stroke &stroke) {
                            for (const auto &point: stroke.points) {
                                float dx = point.x - x;
                                float dy = point.y - y;
                                if (std::sqrt(dx * dx + dy * dy) <= kEraseRadius) {
                                    return true;
                                }
                            }
                            return false;
                        }),
                strokes_.end());
    }

} // namespace gesture_canvas
