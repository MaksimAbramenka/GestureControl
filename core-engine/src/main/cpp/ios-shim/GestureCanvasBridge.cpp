#include "ios-shim/GestureCanvasBridge.h"

#include "input/InputEvent.h"
#include "scene/SceneGraph.h"

using gesture_canvas::InputEvent;
using gesture_canvas::SceneGraph;

struct GCSceneGraph {
    SceneGraph scene;
};

extern "C" {

GCSceneGraph *gc_scene_create(void) {
    return new GCSceneGraph();
}

void gc_scene_destroy(GCSceneGraph *scene) {
    delete scene;
}

void gc_scene_set_brush_color(GCSceneGraph *scene, float r, float g, float b) {
    scene->scene.setBrushColor(r, g, b);
}

void gc_scene_set_brush_size(GCSceneGraph *scene, float size) {
    scene->scene.setBrushSize(size);
}

void gc_scene_submit_input(
    GCSceneGraph *scene, float x, float y, int32_t state, float pressure, int64_t timestamp_ms) {
    InputEvent event{x, y, static_cast<InputEvent::State>(state), pressure, timestamp_ms};
    scene->scene.submitInput(event);
}

void gc_scene_clear(GCSceneGraph *scene) {
    scene->scene.clear();
}

void gc_scene_undo(GCSceneGraph *scene) {
    scene->scene.undo();
}

void gc_scene_redo(GCSceneGraph *scene) {
    scene->scene.redo();
}

bool gc_scene_can_undo(const GCSceneGraph *scene) {
    return scene->scene.canUndo();
}

bool gc_scene_can_redo(const GCSceneGraph *scene) {
    return scene->scene.canRedo();
}

int32_t gc_scene_stroke_count(const GCSceneGraph *scene) {
    return static_cast<int32_t>(scene->scene.visibleStrokes().size());
}

int32_t gc_scene_stroke_point_count(const GCSceneGraph *scene, int32_t stroke_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0;
    return static_cast<int32_t>(strokes[stroke_index].points.size());
}

float gc_scene_stroke_point_x(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    const auto &points = strokes[stroke_index].points;
    if (point_index < 0 || static_cast<size_t>(point_index) >= points.size()) return 0.0f;
    return points[point_index].x;
}

float gc_scene_stroke_point_y(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    const auto &points = strokes[stroke_index].points;
    if (point_index < 0 || static_cast<size_t>(point_index) >= points.size()) return 0.0f;
    return points[point_index].y;
}

float gc_scene_stroke_r(const GCSceneGraph *scene, int32_t stroke_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    return strokes[stroke_index].r;
}

float gc_scene_stroke_g(const GCSceneGraph *scene, int32_t stroke_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    return strokes[stroke_index].g;
}

float gc_scene_stroke_b(const GCSceneGraph *scene, int32_t stroke_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    return strokes[stroke_index].b;
}

float gc_scene_stroke_width(const GCSceneGraph *scene, int32_t stroke_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    return strokes[stroke_index].width;
}

}  // extern "C"
