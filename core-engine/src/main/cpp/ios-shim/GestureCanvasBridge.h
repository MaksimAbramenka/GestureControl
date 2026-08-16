#ifndef GESTURE_CANVAS_IOS_BRIDGE_H
#define GESTURE_CANVAS_IOS_BRIDGE_H

#include <stdbool.h>
#include <stdint.h>

// A plain-C API wrapping gesture_canvas::SceneGraph for Kotlin/Native cinterop -- the iOS
// equivalent of jni/native_lib.cpp, since Kotlin/Native's cinterop tool is C-oriented and has no
// first-class C++ interop. One opaque handle per SceneGraph instance (rather than the JNI side's
// global singleton) so Kotlin-side tests can create an isolated instance per test case.

#ifdef __cplusplus
extern "C" {
#endif

typedef struct GCSceneGraph GCSceneGraph;

GCSceneGraph *gc_scene_create(void);
void gc_scene_destroy(GCSceneGraph *scene);

void gc_scene_set_brush_color(GCSceneGraph *scene, float r, float g, float b);
void gc_scene_set_brush_size(GCSceneGraph *scene, float size);

// state mirrors gesture_canvas::InputEvent::State: 0=IDLE, 1=HOVER, 2=DRAW_START, 3=DRAW_MOVE,
// 4=DRAW_END, 5=ERASE.
void gc_scene_submit_input(
    GCSceneGraph *scene, float x, float y, int32_t state, float pressure, int64_t timestamp_ms);

void gc_scene_clear(GCSceneGraph *scene);
void gc_scene_undo(GCSceneGraph *scene);
void gc_scene_redo(GCSceneGraph *scene);
bool gc_scene_can_undo(const GCSceneGraph *scene);
bool gc_scene_can_redo(const GCSceneGraph *scene);

// Read back the current visible strokes (finalized plus any in-progress one) for inspection --
// used by Kotlin-side tests to assert on stroke output without needing the renderer.
int32_t gc_scene_stroke_count(const GCSceneGraph *scene);
int32_t gc_scene_stroke_point_count(const GCSceneGraph *scene, int32_t stroke_index);
float gc_scene_stroke_point_x(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index);
float gc_scene_stroke_point_y(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index);
float gc_scene_stroke_r(const GCSceneGraph *scene, int32_t stroke_index);
float gc_scene_stroke_g(const GCSceneGraph *scene, int32_t stroke_index);
float gc_scene_stroke_b(const GCSceneGraph *scene, int32_t stroke_index);
float gc_scene_stroke_width(const GCSceneGraph *scene, int32_t stroke_index);

#ifdef __cplusplus
}
#endif

#endif  // GESTURE_CANVAS_IOS_BRIDGE_H
