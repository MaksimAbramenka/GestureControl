#include <gtest/gtest.h>

#include "scene/SceneGraph.h"

using gesture_canvas::InputEvent;
using gesture_canvas::SceneGraph;

TEST(SceneGraphTest, DrawStartThenMoveThenEndProducesOneStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 20});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 3u);
    EXPECT_FLOAT_EQ(scene.strokes()[0].points[0].x, 0.1f);
    EXPECT_FLOAT_EQ(scene.strokes()[0].points[2].x, 0.3f);
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
    scene.submitInput(InputEvent{0.1f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    scene.submitInput(InputEvent{0.2f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 20});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 30});
    ASSERT_EQ(scene.strokes().size(), 1u);
    ASSERT_EQ(scene.strokes()[0].points.size(), 4u);

    // Only the point at 0.3 is within the eraser radius (0.05) of the erase position; the rest
    // of the stroke (spaced 0.1 apart) survives.
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::ERASE, 1.0f, 40});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 3u);
}

TEST(SceneGraphTest, EraseSplitsStrokeWhenMiddleIsErased) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.1f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    scene.submitInput(InputEvent{0.2f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 20});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 30});
    scene.submitInput(InputEvent{0.4f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 40});
    ASSERT_EQ(scene.strokes().size(), 1u);
    ASSERT_EQ(scene.strokes()[0].points.size(), 5u);

    // Erasing the middle point (0.2) splits the stroke into a [0.0, 0.1] piece and a
    // [0.3, 0.4] piece, like a real eraser cutting through a pencil line.
    scene.submitInput(InputEvent{0.2f, 0.0f, InputEvent::State::ERASE, 1.0f, 50});

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
