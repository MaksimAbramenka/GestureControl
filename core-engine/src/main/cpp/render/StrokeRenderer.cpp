#include "render/StrokeRenderer.h"

#include <GLES3/gl3.h>
#include <android/log.h>

#include "render/RibbonTessellator.h"

#define LOG_TAG "GestureCanvasCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// aPosition is in normalized [0,1] canvas space; convert to GL clip space [-1,1] and flip Y,
// since canvas-space Y grows downward but GL clip space Y grows upward.
    const char *kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
void main() {
    vec2 clipPosition = aPosition * 2.0 - 1.0;
    gl_Position = vec4(clipPosition.x, -clipPosition.y, 0.0, 1.0);
}
)";

    const char *kFragmentShaderSource = R"(#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 fragColor;
void main() {
    fragColor = uColor;
}
)";

    GLuint compileShader(GLenum type, const char *source) {
        GLuint shader = glCreateShader(type);
        glShaderSource(shader, 1, &source, nullptr);
        glCompileShader(shader);

        GLint compiled = 0;
        glGetShaderiv(shader, GL_COMPILE_STATUS, &compiled);
        if (!compiled) {
            GLchar log[512];
            glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
            LOGE("Shader compile failed: %s", log);
            glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

} // namespace

namespace gesture_canvas {

    bool StrokeRenderer::init() {
        GLuint vertexShader = compileShader(GL_VERTEX_SHADER, kVertexShaderSource);
        GLuint fragmentShader = compileShader(GL_FRAGMENT_SHADER, kFragmentShaderSource);
        if (vertexShader == 0 || fragmentShader == 0) {
            return false;
        }

        program_ = glCreateProgram();
        glAttachShader(program_, vertexShader);
        glAttachShader(program_, fragmentShader);
        glLinkProgram(program_);

        GLint linked = 0;
        glGetProgramiv(program_, GL_LINK_STATUS, &linked);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        if (!linked) {
            GLchar log[512];
            glGetProgramInfoLog(program_, sizeof(log), nullptr, log);
            LOGE("Program link failed: %s", log);
            return false;
        }

        colorUniformLocation_ = glGetUniformLocation(program_, "uColor");

        glGenVertexArrays(1, &vao_);
        glBindVertexArray(vao_);

        glGenBuffers(1, &vbo_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
        glEnableVertexAttribArray(0);

        glBindVertexArray(0);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        return true;
    }

    void StrokeRenderer::resize(int width, int height) {
        glViewport(0, 0, width, height);
    }

    void StrokeRenderer::draw(const std::vector<Stroke> &strokes) {
        glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        glUseProgram(program_);
        glBindVertexArray(vao_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);

        for (const auto &stroke: strokes) {
            auto vertices = tessellateRibbon(stroke.points, stroke.width);
            if (vertices.empty()) {
                continue;
            }

            glBufferData(
                    GL_ARRAY_BUFFER,
                    static_cast<GLsizeiptr>(vertices.size() * sizeof(RibbonVertex)),
                    vertices.data(), GL_DYNAMIC_DRAW);

            glUniform4f(colorUniformLocation_, stroke.r, stroke.g, stroke.b, 1.0f);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, static_cast<GLsizei>(vertices.size()));
        }

        glBindVertexArray(0);
    }

} // namespace gesture_canvas
