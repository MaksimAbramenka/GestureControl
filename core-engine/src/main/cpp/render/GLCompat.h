#pragma once

// A single switch point for the GL ES header and error-logging macro so StrokeRenderer.cpp
// itself stays platform-agnostic -- Android and iOS ship the identical GL ES 3.0 API under
// different header paths, and only logging needs a platform-specific backend.

#if defined(__ANDROID__)
#include <GLES3/gl3.h>
#include <android/log.h>
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "GestureCanvasCore", __VA_ARGS__)
#elif defined(__APPLE__)
#include <OpenGLES/ES3/gl.h>
#include <cstdio>
#define LOGE(...)                              \
    do {                                        \
        fprintf(stderr, "GestureCanvasCore: "); \
        fprintf(stderr, __VA_ARGS__);            \
        fprintf(stderr, "\n");                   \
    } while (0)
#else
#error "GLCompat.h: unsupported platform"
#endif
