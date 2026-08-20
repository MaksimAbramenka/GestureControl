#include "ios-shim/GestureCanvasBridge.h"

#include <cstring>
#include <vector>

#include "input/InputEvent.h"
#include "ios-shim/EaglContext.h"
#include "render/GLCompat.h"
#include "render/StrokeRenderer.h"
#include "scene/SceneGraph.h"

using gesture_canvas::EaglContext;
using gesture_canvas::InputEvent;
using gesture_canvas::SceneGraph;
using gesture_canvas::StrokeRenderer;

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
        GCSceneGraph *scene, float x, float y, int32_t state, float pressure,
        int64_t timestamp_ms) {
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

float
gc_scene_stroke_point_x(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index) {
    auto strokes = scene->scene.visibleStrokes();
    if (stroke_index < 0 || static_cast<size_t>(stroke_index) >= strokes.size()) return 0.0f;
    const auto &points = strokes[stroke_index].points;
    if (point_index < 0 || static_cast<size_t>(point_index) >= points.size()) return 0.0f;
    return points[point_index].x;
}

float
gc_scene_stroke_point_y(const GCSceneGraph *scene, int32_t stroke_index, int32_t point_index) {
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

struct GCRenderer {
    EaglContext eagl;
    StrokeRenderer renderer;
    GLuint framebuffer = 0;
    GLuint colorRenderbuffer = 0;
    int32_t width = 0;
    int32_t height = 0;
    bool ready = false;
};

extern "C" {

GCRenderer *gc_renderer_create(void) {
    return new GCRenderer();
}

void gc_renderer_destroy(GCRenderer *renderer) {
    if (renderer == nullptr) return;
    if (renderer->ready && renderer->eagl.makeCurrent()) {
        if (renderer->colorRenderbuffer != 0)
            glDeleteRenderbuffers(1, &renderer->colorRenderbuffer);
        if (renderer->framebuffer != 0) glDeleteFramebuffers(1, &renderer->framebuffer);
    }
    delete renderer;
}

bool gc_renderer_init(GCRenderer *renderer, int32_t width, int32_t height) {
    if (renderer == nullptr || width <= 0 || height <= 0) return false;

    if (!renderer->eagl.init() || !renderer->eagl.makeCurrent()) {
        LOGE("EaglContext init/makeCurrent failed");
        return false;
    }

    glGenFramebuffers(1, &renderer->framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, renderer->framebuffer);

    glGenRenderbuffers(1, &renderer->colorRenderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, renderer->colorRenderbuffer);
    glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA8, width, height);
    glFramebufferRenderbuffer(
            GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderer->colorRenderbuffer);

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("Offscreen framebuffer incomplete");
        return false;
    }

    if (!renderer->renderer.init()) {
        LOGE("StrokeRenderer init failed");
        return false;
    }
    renderer->renderer.resize(width, height);

    renderer->width = width;
    renderer->height = height;
    renderer->ready = true;
    return true;
}

void gc_renderer_draw(GCRenderer *renderer, const GCSceneGraph *scene) {
    if (renderer == nullptr || scene == nullptr || !renderer->ready) return;
    if (!renderer->eagl.makeCurrent()) return;

    glBindFramebuffer(GL_FRAMEBUFFER, renderer->framebuffer);
    renderer->renderer.draw(scene->scene.visibleStrokes());
}

bool gc_renderer_capture(const GCRenderer *renderer, uint8_t *out_pixels, int32_t buffer_size) {
    if (renderer == nullptr || out_pixels == nullptr || !renderer->ready) return false;
    const auto requiredSize = static_cast<int64_t>(renderer->width) * renderer->height * 4;
    if (buffer_size < requiredSize) return false;
    if (!renderer->eagl.makeCurrent()) return false;

    glBindFramebuffer(GL_FRAMEBUFFER, renderer->framebuffer);

    std::vector<uint8_t> pixels(static_cast<size_t>(requiredSize));
    glReadPixels(0, 0, renderer->width, renderer->height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());

    // glReadPixels is bottom-up; flip to top-down to match a conventional image buffer.
    const auto rowBytes = static_cast<size_t>(renderer->width) * 4;
    for (int row = 0; row < renderer->height; ++row) {
        const uint8_t *src =
                pixels.data() + static_cast<size_t>(renderer->height - 1 - row) * rowBytes;
        uint8_t *dst = out_pixels + static_cast<size_t>(row) * rowBytes;
        std::memcpy(dst, src, rowBytes);
    }
    return true;
}

bool gc_renderer_init_onscreen(
        GCRenderer *renderer, void *ca_layer, int32_t *out_width, int32_t *out_height) {
    if (renderer == nullptr || ca_layer == nullptr) return false;

    if (!renderer->eagl.init() || !renderer->eagl.makeCurrent()) {
        LOGE("EaglContext init/makeCurrent failed");
        return false;
    }

    glGenFramebuffers(1, &renderer->framebuffer);
    glBindFramebuffer(GL_FRAMEBUFFER, renderer->framebuffer);

    glGenRenderbuffers(1, &renderer->colorRenderbuffer);
    glBindRenderbuffer(GL_RENDERBUFFER, renderer->colorRenderbuffer);
    if (!renderer->eagl.bindDrawable(ca_layer)) {
        LOGE("EaglContext bindDrawable failed");
        return false;
    }
    glFramebufferRenderbuffer(
            GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, renderer->colorRenderbuffer);

    GLint width = 0;
    GLint height = 0;
    glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, &width);
    glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, &height);
    if (width <= 0 || height <= 0) {
        LOGE("Drawable-bound renderbuffer has no size");
        return false;
    }

    if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
        LOGE("On-screen framebuffer incomplete");
        return false;
    }

    if (!renderer->renderer.init()) {
        LOGE("StrokeRenderer init failed");
        return false;
    }
    renderer->renderer.resize(width, height);

    renderer->width = width;
    renderer->height = height;
    renderer->ready = true;
    if (out_width != nullptr) *out_width = width;
    if (out_height != nullptr) *out_height = height;
    return true;
}

bool gc_renderer_present(const GCRenderer *renderer) {
    if (renderer == nullptr || !renderer->ready) return false;
    if (!renderer->eagl.makeCurrent()) return false;
    glBindRenderbuffer(GL_RENDERBUFFER, renderer->colorRenderbuffer);
    return renderer->eagl.presentRenderbuffer();
}

}  // extern "C"
