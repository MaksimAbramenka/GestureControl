cmake_minimum_required(VERSION 3.22.1)
project(gesture_canvas_core_tests CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)
FetchContent_Declare(
    googletest
    GIT_REPOSITORY https://github.com/google/googletest.git
    GIT_TAG v1.17.0
)
set(gtest_force_shared_crt ON CACHE BOOL "" FORCE)
FetchContent_MakeAvailable(googletest)

enable_testing()

add_executable(gesture_canvas_core_tests
    input/InputEventTest.cpp
    input/OneEuroFilterTest.cpp
    input/PointSmootherTest.cpp
    scene/SceneGraphTest.cpp
    render/RibbonTessellatorTest.cpp
    ../../main/cpp/scene/SceneGraph.cpp
    ../../main/cpp/render/RibbonTessellator.cpp
    ../../main/cpp/input/OneEuroFilter.cpp
    ../../main/cpp/input/PointSmoother.cpp
)

target_include_directories(gesture_canvas_core_tests PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../main/cpp
)

target_link_libraries(gesture_canvas_core_tests
    GTest::gtest_main
)

include(GoogleTest)
gtest_discover_tests(gesture_canvas_core_tests)
