#include "render/TriangleRenderer.h"

#include <GLES3/gl3.h>
#include <android/log.h>

#define LOG_TAG "GestureCanvasCore"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

const char *kVertexShaderSource = R"(#version 300 es
layout(location = 0) in vec2 aPosition;
void main() {
    gl_Position = vec4(aPosition, 0.0, 1.0);
}
)";

const char *kFragmentShaderSource = R"(#version 300 es
precision mediump float;
out vec4 fragColor;
void main() {
    fragColor = vec4(0.2, 0.6, 1.0, 1.0);
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

bool TriangleRenderer::init() {
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

    static const float vertices[] = {
        0.0f, 0.5f,
        -0.5f, -0.5f,
        0.5f, -0.5f,
    };

    glGenVertexArrays(1, &vao_);
    glBindVertexArray(vao_);

    glGenBuffers(1, &vbo_);
    glBindBuffer(GL_ARRAY_BUFFER, vbo_);
    glBufferData(GL_ARRAY_BUFFER, sizeof(vertices), vertices, GL_STATIC_DRAW);

    glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 0, nullptr);
    glEnableVertexAttribArray(0);

    glBindVertexArray(0);

    return true;
}

void TriangleRenderer::resize(int width, int height) {
    glViewport(0, 0, width, height);
}

void TriangleRenderer::draw() {
    glClearColor(0.05f, 0.05f, 0.08f, 1.0f);
    glClear(GL_COLOR_BUFFER_BIT);

    glUseProgram(program_);
    glBindVertexArray(vao_);
    glDrawArrays(GL_TRIANGLES, 0, 3);
    glBindVertexArray(0);
}

} // namespace gesture_canvas
