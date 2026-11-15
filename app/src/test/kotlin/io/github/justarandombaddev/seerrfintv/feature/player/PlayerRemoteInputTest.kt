package io.github.justarandombaddev.seerrfintv.feature.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerRemoteInputTest {
    @Test
    fun `center and media play pause toggle playback`() {
        assertEquals(PlayerCommand.Toggle, playerCommandFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(PlayerCommand.Toggle, playerCommandFor(KeyEvent.KEYCODE_ENTER))
        assertEquals(PlayerCommand.Toggle, playerCommandFor(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
    }

    @Test
    fun `dedicated media keys preserve their meaning`() {
        assertEquals(PlayerCommand.Play, playerCommandFor(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(PlayerCommand.Pause, playerCommandFor(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(PlayerCommand.SeekBack, playerCommandFor(KeyEvent.KEYCODE_MEDIA_REWIND))
        assertEquals(PlayerCommand.SeekForward, playerCommandFor(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
    }

    @Test
    fun `horizontal d pad seeks and unrelated keys remain available to the system`() {
        assertEquals(PlayerCommand.SeekBack, playerCommandFor(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(PlayerCommand.SeekForward, playerCommandFor(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertNull(playerCommandFor(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(playerCommandFor(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `held toggle key is consumed once until key up`() {
        val tracker = PlayerKeyInputTracker()

        assertEquals(
            PlayerKeyHandling(consumed = true, command = PlayerCommand.Toggle),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, isKeyDown = true),
        )
        assertEquals(
            PlayerKeyHandling(consumed = true),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, isKeyDown = true),
        )
        assertEquals(
            PlayerKeyHandling(consumed = true),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, isKeyDown = false),
        )
        assertEquals(
            PlayerKeyHandling(consumed = true, command = PlayerCommand.Toggle),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER, isKeyDown = true),
        )
    }

    @Test
    fun `held seek key repeats while unknown keys remain unhandled`() {
        val tracker = PlayerKeyInputTracker()

        assertEquals(
            PlayerKeyHandling(consumed = true, command = PlayerCommand.SeekForward),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, isKeyDown = true),
        )
        assertEquals(
            PlayerKeyHandling(consumed = true, command = PlayerCommand.SeekForward),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, isKeyDown = true),
        )
        assertEquals(
            PlayerKeyHandling(consumed = false),
            tracker.onKeyEvent(KeyEvent.KEYCODE_DPAD_UP, isKeyDown = true),
        )
    }
}
