#pragma once

// A single switch point for the GL header, error-logging macro, and GLSL source prefix so
// StrokeRenderer.cpp itself stays platform-agnostic -- Android and iOS ship the identical GL ES
// 3.0 API under different header paths, needing only a different logging backend; desktop macOS
// has no GLES implementation of its own (that's what a translation layer like ANGLE is for, and
// Phase 3b deliberately avoided depending on one -- see the project plan, §6b.2/§6b.3), so it
// gets real desktop OpenGL 3.3 core profile instead, which needs a different GLSL version pragma
// and drops the GLES-only `precision` qualifier -- centralized here as string macros so
// StrokeRenderer.cpp's shader source itself doesn't need its own platform #ifdef.

#if defined(__ANDROID__)
#include <GLES3/gl3.h>
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GestureCanvasCore", __VA_ARGS__)
#define GC_GLSL_VERSION "#version 300 es\n"
#define GC_GLSL_PRECISION "precision mediump float;\n"
#elif defined(__APPLE__)
#include <TargetConditionals.h>
#include <cstdio>
#define LOGE(...)                              \
    do {                                        \
        fprintf(stderr, "GestureCanvasCore: "); \
        fprintf(stderr, __VA_ARGS__);            \
        fprintf(stderr, "\n");                   \
    } while (0)
#if TARGET_OS_IPHONE
#include <OpenGLES/ES3/gl.h>
#define GC_GLSL_VERSION "#version 300 es\n"
#define GC_GLSL_PRECISION "precision mediump float;\n"
#else
#include <OpenGL/gl3.h>
#define GC_GLSL_VERSION "#version 330 core\n"
#define GC_GLSL_PRECISION ""
#endif
#else
#error "GLCompat.h: unsupported platform"
#endif
