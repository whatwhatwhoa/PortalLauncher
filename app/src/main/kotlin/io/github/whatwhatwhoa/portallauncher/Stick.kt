package io.github.whatwhatwhoa.portallauncher

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * The left stick, read by the launcher rather than through Android's own D-pad conversion, which
 * takes the two axes apart at 0.5: a run along the row that drifted a little down sent Down too
 * and dropped focus into the dock, and every push repeated at one flat rate. Here only the
 * stronger axis counts, with some give (in at [ENTER], out under [RELEASE]); a push steps once,
 * then repeats after Android's key-repeat timeout, quicker the further it is pushed ([SLOW] to
 * [FAST]). Each step goes in as a D-pad key ([step], the repeat count with it), so every gate on
 * keys holds for the stick as for the D-pad.
 *
 * The hat (the Portal's D-pad) comes in the same events and stays Android's. An event is only
 * taken while the hat is at rest in it and in the last one Android saw, and while Android holds
 * no push of the stick itself; so Android always sees its own pushes let go, and none of its
 * repeats runs on.
 */
class Stick(private val step: (keyCode: Int, repeat: Int) -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    // The D-pad key the stick holds, how far along it, and the repeats so far.
    private var held: Int? = null
    private var reach = 0f
    private var repeats = 0
    private var device = 0
    // What the last event Android saw said: its conversion acts on those.
    private var hatSeen = false
    private var stickSeen = false

    private val repeat = object : Runnable {
        override fun run() {
            val key = held ?: return
            // A controller unplugged mid-push sends no release: its repeat ends with it.
            if (InputDevice.getDevice(device) == null) return cancel()
            step(key, ++repeats)
            handler.postDelayed(this, interval())
        }
    }

    /** Whether the launcher takes [event] (a joystick move); [typing]: a text field has the keyboard. */
    fun takes(event: MotionEvent, typing: Boolean): Boolean {
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val hat = pushed(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        val ours = !typing && !hat && !hatSeen && !stickSeen
        if (!ours) {
            cancel()
            return false
        }
        device = event.deviceId
        follow(x, y)
        return max(abs(x), abs(y)) > IDLE
    }

    /** [event] went on to Android unhandled: what its conversion now holds. */
    fun passed(event: MotionEvent) {
        hatSeen = pushed(event.getAxisValue(MotionEvent.AXIS_HAT_X), event.getAxisValue(MotionEvent.AXIS_HAT_Y))
        stickSeen = pushed(event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
    }

    /** Stops a push and its repeats (released, out of view). */
    fun cancel() {
        handler.removeCallbacks(repeat)
        held = null
    }

    /** As the window loses focus Android forgets what its conversion held; so does this. */
    fun reset() {
        cancel()
        hatSeen = false
        stickSeen = false
    }

    private fun follow(x: Float, y: Float) {
        held?.let { key ->
            val along = along(key, x, y)
            if (along >= RELEASE) {
                reach = along
                return
            }
            cancel()
        }
        val across = abs(x) >= abs(y)
        val value = if (across) abs(x) else abs(y)
        if (value < ENTER) return
        val key = when {
            across && x < 0 -> KeyEvent.KEYCODE_DPAD_LEFT
            across -> KeyEvent.KEYCODE_DPAD_RIGHT
            y < 0 -> KeyEvent.KEYCODE_DPAD_UP
            else -> KeyEvent.KEYCODE_DPAD_DOWN
        }
        held = key
        reach = value
        repeats = 0
        step(key, 0)
        handler.postDelayed(repeat, ViewConfiguration.getKeyRepeatTimeout().toLong())
    }

    // How far the stick is pushed toward the held key's direction (negative: away from it).
    private fun along(key: Int, x: Float, y: Float) = when (key) {
        KeyEvent.KEYCODE_DPAD_LEFT -> -x
        KeyEvent.KEYCODE_DPAD_RIGHT -> x
        KeyEvent.KEYCODE_DPAD_UP -> -y
        else -> y
    }

    private fun interval(): Long {
        val t = ((reach - ENTER) / (1f - ENTER)).coerceIn(0f, 1f)
        return (SLOW - (SLOW - FAST) * t).roundToLong()
    }

    // Android's conversion counts an axis as pushed from 0.5, the hat's as the stick's.
    private fun pushed(x: Float, y: Float) = abs(x) >= HALF || abs(y) >= HALF

    private companion object {
        const val ENTER = 0.5f
        const val RELEASE = 0.35f
        // Under this the stick is at rest: such an event goes on to Android, which does nothing with it.
        const val IDLE = 0.2f
        const val HALF = 0.5f
        // The repeat's interval, ms: at the edge of a push, and pushed all the way.
        const val SLOW = 120f
        const val FAST = 45f
    }
}
