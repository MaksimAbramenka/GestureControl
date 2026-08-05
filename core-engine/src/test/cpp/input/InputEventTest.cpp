#include <gtest/gtest.h>

#include "input/InputEvent.h"

using gesture_canvas::InputEvent;

TEST(InputEventStateTest, OrdinalsMatchKotlinConstants) {
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::IDLE), 0);
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::HOVER), 1);
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::DRAW_START), 2);
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::DRAW_MOVE), 3);
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::DRAW_END), 4);
    EXPECT_EQ(static_cast<int32_t>(InputEvent::State::ERASE), 5);
}
