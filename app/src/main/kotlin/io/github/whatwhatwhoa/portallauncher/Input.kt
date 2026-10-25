package io.github.whatwhatwhoa.portallauncher

import android.view.KeyEvent

/**
 * What a key means, independent of the screen. X and Y are named by face position; the screens
 * decide what they do. Only movement repeats when held, so a held A never launches twice.
 */
enum class Action(val repeats: Boolean = false) {
    Left(true), Right(true), Up(true), Down(true),
    Open, Back, Options, Select, X, Y,
    PagePrev(true), PageNext(true), First, Last, R3,
}

/**
 * The one key table. On the owner's Portal A is the bottom button (Xbox layout), which Android
 * reports as BUTTON_A; a Nintendo-style swap is a change here only. The left stick and the hat
 * need no entry: Android turns unhandled joystick motion into D-pad keys, with repeat.
 */
private val keyActions = mapOf(
    KeyEvent.KEYCODE_DPAD_LEFT to Action.Left,
    KeyEvent.KEYCODE_DPAD_RIGHT to Action.Right,
    KeyEvent.KEYCODE_DPAD_UP to Action.Up,
    KeyEvent.KEYCODE_DPAD_DOWN to Action.Down,
    KeyEvent.KEYCODE_BUTTON_A to Action.Open,
    KeyEvent.KEYCODE_DPAD_CENTER to Action.Open,
    KeyEvent.KEYCODE_ENTER to Action.Open,
    KeyEvent.KEYCODE_NUMPAD_ENTER to Action.Open,
    KeyEvent.KEYCODE_BUTTON_B to Action.Back,
    KeyEvent.KEYCODE_BACK to Action.Back,
    KeyEvent.KEYCODE_ESCAPE to Action.Back,
    KeyEvent.KEYCODE_BUTTON_X to Action.X,
    KeyEvent.KEYCODE_BUTTON_Y to Action.Y,
    KeyEvent.KEYCODE_BUTTON_START to Action.Options,
    KeyEvent.KEYCODE_MENU to Action.Options,
    KeyEvent.KEYCODE_BUTTON_SELECT to Action.Select,
    KeyEvent.KEYCODE_BUTTON_L1 to Action.PagePrev,
    KeyEvent.KEYCODE_PAGE_UP to Action.PagePrev,
    KeyEvent.KEYCODE_BUTTON_R1 to Action.PageNext,
    KeyEvent.KEYCODE_PAGE_DOWN to Action.PageNext,
    KeyEvent.KEYCODE_BUTTON_L2 to Action.First,
    KeyEvent.KEYCODE_MOVE_HOME to Action.First,
    KeyEvent.KEYCODE_BUTTON_R2 to Action.Last,
    KeyEvent.KEYCODE_MOVE_END to Action.Last,
    KeyEvent.KEYCODE_BUTTON_THUMBR to Action.R3,
)

fun actionFor(event: KeyEvent): Action? = keyActions[event.keyCode]
