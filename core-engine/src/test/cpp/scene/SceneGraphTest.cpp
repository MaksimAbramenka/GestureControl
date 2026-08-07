#include <gtest/gtest.h>

#include "scene/SceneGraph.h"

using gesture_canvas::InputEvent;
using gesture_canvas::Point2D;
using gesture_canvas::SceneGraph;

TEST(SceneGraphTest, DrawStartThenMoveThenEndProducesOneStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 20});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 3u);
    // The first point of a stroke always passes through the smoothing filter unchanged.
    EXPECT_FLOAT_EQ(scene.strokes()[0].points[0].x, 0.1f);
}

TEST(SceneGraphTest, DrawPointsAreSmoothedNotRaw) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    // A large, sudden jump shouldn't be applied instantly if smoothing is wired in.
    scene.submitInput(InputEvent{10.0f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 33});

    auto visible = scene.visibleStrokes();
    ASSERT_EQ(visible.size(), 1u);
    ASSERT_EQ(visible[0].points.size(), 2u);
    EXPECT_GT(visible[0].points[1].x, 0.0f);
    EXPECT_LT(visible[0].points[1].x, 10.0f);
}

TEST(SceneGraphTest, IdleAndHoverProduceNoStrokes) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::IDLE, 1.0f, 0});
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::HOVER, 1.0f, 10});

    EXPECT_TRUE(scene.strokes().empty());
}

TEST(SceneGraphTest, EraseRemovesEntireStrokeWhenAllPointsWithinRadius) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.11f, 0.1f, InputEvent::State::DRAW_END, 1.0f, 10});
    ASSERT_EQ(scene.strokes().size(), 1u);

    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::ERASE, 1.0f, 20});

    EXPECT_TRUE(scene.strokes().empty());
}

TEST(SceneGraphTest, EraseTrimsOnlyPointsWithinRadius) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 33});
    scene.submitInput(InputEvent{0.6f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 66});
    scene.submitInput(InputEvent{0.9f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 99});
    ASSERT_EQ(scene.strokes().size(), 1u);
    ASSERT_EQ(scene.strokes()[0].points.size(), 4u);

    // Erase at the actual (smoothed, not raw) position of the last point -- points are far
    // enough apart post-smoothing that only that one point falls within the eraser radius.
    Point2D lastPoint = scene.strokes()[0].points[3];
    scene.submitInput(InputEvent{lastPoint.x, lastPoint.y, InputEvent::State::ERASE, 1.0f, 100});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 3u);
}

TEST(SceneGraphTest, EraseSplitsStrokeWhenMiddleIsErased) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 33});
    scene.submitInput(InputEvent{0.6f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 66});
    scene.submitInput(InputEvent{0.9f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 99});
    scene.submitInput(InputEvent{1.2f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 132});
    ASSERT_EQ(scene.strokes().size(), 1u);
    ASSERT_EQ(scene.strokes()[0].points.size(), 5u);

    // Erasing at the actual (smoothed) middle point splits the stroke into two 2-point pieces,
    // like a real eraser cutting through a pencil line.
    Point2D middlePoint = scene.strokes()[0].points[2];
    scene.submitInput(InputEvent{middlePoint.x, middlePoint.y, InputEvent::State::ERASE, 1.0f, 133});

    ASSERT_EQ(scene.strokes().size(), 2u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 2u);
    EXPECT_EQ(scene.strokes()[1].points.size(), 2u);
}

TEST(SceneGraphTest, EraseDoesNotRemoveDistantStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.15f, 0.15f, InputEvent::State::DRAW_END, 1.0f, 10});
    ASSERT_EQ(scene.strokes().size(), 1u);

    scene.submitInput(InputEvent{0.9f, 0.9f, InputEvent::State::ERASE, 1.0f, 20});

    EXPECT_EQ(scene.strokes().size(), 1u);
}

TEST(SceneGraphTest, BrushColorAndSizeApplyToNewStrokesOnly) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 10});

    scene.setBrushColor(1.0f, 0.0f, 0.0f);
    scene.setBrushSize(0.05f);
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_END, 1.0f, 30});

    ASSERT_EQ(scene.strokes().size(), 2u);
    EXPECT_FLOAT_EQ(scene.strokes()[1].r, 1.0f);
    EXPECT_FLOAT_EQ(scene.strokes()[1].g, 0.0f);
    EXPECT_FLOAT_EQ(scene.strokes()[1].width, 0.05f);
}

TEST(SceneGraphTest, StartingNewStrokeWhileDrawingFinalizesThePrevious) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_START, 1.0f, 20});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 2u);
}

TEST(SceneGraphTest, VisibleStrokesIncludeTheInProgressStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_MOVE, 1.0f, 10});

    auto visible = scene.visibleStrokes();

    EXPECT_TRUE(scene.strokes().empty());
    ASSERT_EQ(visible.size(), 1u);
    EXPECT_EQ(visible[0].points.size(), 2u);
}

TEST(SceneGraphTest, VisibleStrokesOmitInProgressStrokeWhenNotDrawing) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});

    auto visible = scene.visibleStrokes();

    ASSERT_EQ(visible.size(), 1u);
    EXPECT_EQ(visible[0].points.size(), 2u);
}
