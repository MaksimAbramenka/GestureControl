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

TEST(SceneGraphTest, BrushColorAndSizeChangeMidStrokeUpdatesTheInProgressStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.1f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 10});

    scene.setBrushColor(1.0f, 0.0f, 0.0f);
    scene.setBrushSize(0.05f);
    scene.submitInput(InputEvent{0.2f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 20});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 30});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_FLOAT_EQ(scene.strokes()[0].r, 1.0f);
    EXPECT_FLOAT_EQ(scene.strokes()[0].g, 0.0f);
    EXPECT_FLOAT_EQ(scene.strokes()[0].width, 0.05f);
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

TEST(SceneGraphTest, ClearRemovesAllFinishedStrokes) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.6f, 0.6f, InputEvent::State::DRAW_END, 1.0f, 30});
    ASSERT_EQ(scene.strokes().size(), 2u);

    scene.clear();

    EXPECT_TRUE(scene.strokes().empty());
    EXPECT_TRUE(scene.visibleStrokes().empty());
}

TEST(SceneGraphTest, ClearAlsoDiscardsAnInProgressStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_MOVE, 1.0f, 10});
    ASSERT_FALSE(scene.visibleStrokes().empty());

    scene.clear();

    EXPECT_TRUE(scene.visibleStrokes().empty());
}

TEST(SceneGraphTest, DrawingResumesNormallyAfterClear) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.clear();

    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.4f, 0.4f, InputEvent::State::DRAW_END, 1.0f, 30});

    ASSERT_EQ(scene.strokes().size(), 1u);
    // The first point of a fresh stroke always passes through the smoothing filter unchanged,
    // confirming the smoother's state was reset too, not just the strokes.
    EXPECT_FLOAT_EQ(scene.strokes()[0].points[0].x, 0.3f);
}

TEST(SceneGraphTest, VisibleStrokesOmitInProgressStrokeWhenNotDrawing) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});

    auto visible = scene.visibleStrokes();

    ASSERT_EQ(visible.size(), 1u);
    EXPECT_EQ(visible[0].points.size(), 2u);
}

TEST(SceneGraphTest, CanUndoAndCanRedoStartFalse) {
    SceneGraph scene;

    EXPECT_FALSE(scene.canUndo());
    EXPECT_FALSE(scene.canRedo());
}

TEST(SceneGraphTest, UndoRevertsTheLastCompletedStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.6f, 0.6f, InputEvent::State::DRAW_END, 1.0f, 30});
    ASSERT_EQ(scene.strokes().size(), 2u);

    scene.undo();

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_TRUE(scene.canRedo());
}

TEST(SceneGraphTest, RedoReappliesAnUndoneStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.undo();
    ASSERT_TRUE(scene.strokes().empty());

    scene.redo();

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_FALSE(scene.canRedo());
}

TEST(SceneGraphTest, UndoWithNothingToUndoIsANoOp) {
    SceneGraph scene;

    scene.undo();

    EXPECT_TRUE(scene.strokes().empty());
    EXPECT_FALSE(scene.canRedo());
}

TEST(SceneGraphTest, RedoWithNothingToRedoIsANoOp) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});

    scene.redo();

    ASSERT_EQ(scene.strokes().size(), 1u);
}

TEST(SceneGraphTest, NewStrokeAfterUndoClearsTheRedoStack) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.undo();
    ASSERT_TRUE(scene.canRedo());

    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.6f, 0.6f, InputEvent::State::DRAW_END, 1.0f, 30});

    EXPECT_FALSE(scene.canRedo());
}

TEST(SceneGraphTest, UndoRevertsAWholeEraseGestureNotJustOneFrame) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 33});
    scene.submitInput(InputEvent{0.6f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 66});
    scene.submitInput(InputEvent{0.9f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 99});
    ASSERT_EQ(scene.strokes()[0].points.size(), 4u);

    // A single held erase gesture spans several ERASE frames -- this should only cost one undo
    // step, not one per frame.
    Point2D first = scene.strokes()[0].points[0];
    Point2D second = scene.strokes()[0].points[1];
    scene.submitInput(InputEvent{first.x, first.y, InputEvent::State::ERASE, 1.0f, 100});
    scene.submitInput(InputEvent{second.x, second.y, InputEvent::State::ERASE, 1.0f, 110});
    ASSERT_EQ(scene.strokes()[0].points.size(), 2u);

    scene.undo();

    ASSERT_EQ(scene.strokes()[0].points.size(), 4u);
    // One more undo step remains: the stroke's own creation (from DRAW_START).
    EXPECT_TRUE(scene.canUndo());
}

TEST(SceneGraphTest, SeparateEraseGesturesAreSeparateUndoSteps) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.0f, 0.0f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.3f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 33});
    scene.submitInput(InputEvent{0.6f, 0.0f, InputEvent::State::DRAW_MOVE, 1.0f, 66});
    scene.submitInput(InputEvent{0.9f, 0.0f, InputEvent::State::DRAW_END, 1.0f, 99});

    Point2D first = scene.strokes()[0].points[0];
    scene.submitInput(InputEvent{first.x, first.y, InputEvent::State::ERASE, 1.0f, 100});
    // Hand moves away between erase passes -- HOVER breaks the erase session.
    scene.submitInput(InputEvent{0.5f, 0.5f, InputEvent::State::HOVER, 1.0f, 105});
    Point2D second = scene.strokes()[0].points[0];
    scene.submitInput(InputEvent{second.x, second.y, InputEvent::State::ERASE, 1.0f, 110});
    ASSERT_EQ(scene.strokes()[0].points.size(), 2u);

    scene.undo();
    ASSERT_EQ(scene.strokes()[0].points.size(), 3u);
    EXPECT_TRUE(scene.canUndo());

    scene.undo();
    ASSERT_EQ(scene.strokes()[0].points.size(), 4u);
    // One more undo step remains: the stroke's own creation (from DRAW_START).
    EXPECT_TRUE(scene.canUndo());
}

TEST(SceneGraphTest, ClearWipesUndoAndRedoHistory) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    ASSERT_TRUE(scene.canUndo());

    scene.clear();

    ASSERT_TRUE(scene.strokes().empty());
    EXPECT_FALSE(scene.canUndo());
    EXPECT_FALSE(scene.canRedo());

    scene.undo();
    ASSERT_TRUE(scene.strokes().empty());
}

// A brief gesture-classification flicker (or the fingertip grazing the PiP-suppression zone)
// can end a stroke and immediately re-start one a moment later at nearly the same spot, even
// though the user never stopped drawing. These strokes should merge into one, both in storage
// and for undo -- otherwise a single undo doesn't remove what looks like one continuous line.
TEST(SceneGraphTest, ADrawStartSoonAfterAndNearWhereTheLastStrokeEndedContinuesIt) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    ASSERT_EQ(scene.strokes().size(), 1u);

    // Close in both time and space to the previous stroke's end point.
    scene.submitInput(InputEvent{0.205f, 0.205f, InputEvent::State::DRAW_START, 1.0f, 60});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 70});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 4u);
}

TEST(SceneGraphTest, MergedStrokesOnlyCostOneUndoStep) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.submitInput(InputEvent{0.205f, 0.205f, InputEvent::State::DRAW_START, 1.0f, 60});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 70});
    scene.submitInput(InputEvent{0.305f, 0.305f, InputEvent::State::DRAW_START, 1.0f, 120});
    scene.submitInput(InputEvent{0.4f, 0.4f, InputEvent::State::DRAW_END, 1.0f, 130});
    ASSERT_EQ(scene.strokes()[0].points.size(), 6u);

    scene.undo();

    EXPECT_TRUE(scene.strokes().empty());
    EXPECT_FALSE(scene.canUndo());
}

TEST(SceneGraphTest, ADrawStartFarInTimeFromTheLastStrokeStaysSeparate) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});

    // Same position, but a full second later -- a deliberate new stroke, not a flicker.
    scene.submitInput(InputEvent{0.205f, 0.205f, InputEvent::State::DRAW_START, 1.0f, 1010});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 1020});

    ASSERT_EQ(scene.strokes().size(), 2u);
}

TEST(SceneGraphTest, ADrawStartFarInSpaceFromTheLastStrokeStaysSeparate) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});

    // Soon after, but on the other side of the canvas.
    scene.submitInput(InputEvent{0.8f, 0.8f, InputEvent::State::DRAW_START, 1.0f, 30});
    scene.submitInput(InputEvent{0.9f, 0.9f, InputEvent::State::DRAW_END, 1.0f, 40});

    ASSERT_EQ(scene.strokes().size(), 2u);
}

TEST(SceneGraphTest, ClearPreventsTheNextDrawFromResumingTheClearedStroke) {
    SceneGraph scene;
    scene.submitInput(InputEvent{0.1f, 0.1f, InputEvent::State::DRAW_START, 1.0f, 0});
    scene.submitInput(InputEvent{0.2f, 0.2f, InputEvent::State::DRAW_END, 1.0f, 10});
    scene.clear();

    scene.submitInput(InputEvent{0.205f, 0.205f, InputEvent::State::DRAW_START, 1.0f, 20});
    scene.submitInput(InputEvent{0.3f, 0.3f, InputEvent::State::DRAW_END, 1.0f, 30});

    ASSERT_EQ(scene.strokes().size(), 1u);
    EXPECT_EQ(scene.strokes()[0].points.size(), 2u);
}
