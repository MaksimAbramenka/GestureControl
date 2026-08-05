cmake_minimum_required(VERSION 3.22.1)
project(gesture_canvas_core)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(gesture_canvas_core SHARED
    jni/native_lib.cpp
    render/EglContext.cpp
    render/TriangleRenderer.cpp
)

target_include_directories(gesture_canvas_core PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)
find_library(egl-lib EGL)
find_library(gles3-lib GLESv3)

target_link_libraries(gesture_canvas_core
    ${log-lib}
    ${android-lib}
    ${egl-lib}
    ${gles3-lib}
)
