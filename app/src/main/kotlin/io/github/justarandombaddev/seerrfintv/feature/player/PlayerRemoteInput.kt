package io.github.justarandombaddev.seerrfintv.feature.player

import android.view.KeyEvent

enum class PlayerCommand {
    Toggle,
    Play,
    Pause,
    SeekBack,
    SeekForward,
}

internal fun playerCommandFor(keyCode: Int): PlayerCommand? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        -> PlayerCommand.Toggle

        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerCommand.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerCommand.Pause
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_MEDIA_REWIND,
        -> PlayerCommand.SeekBack

        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        -> PlayerCommand.SeekForward

        else -> null
    }

internal data class PlayerKeyHandling(
    val consumed: Boolean,
    val command: PlayerCommand? = null,
)

internal class PlayerKeyInputTracker {
    private val pressedKeyCodes = mutableSetOf<Int>()

    fun onKeyEvent(
        keyCode: Int,
        isKeyDown: Boolean,
    ): PlayerKeyHandling {
        val command = playerCommandFor(keyCode) ?: return PlayerKeyHandling(consumed = false)
        if (!isKeyDown) {
            pressedKeyCodes.remove(keyCode)
            return PlayerKeyHandling(consumed = true)
        }

        val firstKeyDown = pressedKeyCodes.add(keyCode)
        val repeatable = command == PlayerCommand.SeekBack || command == PlayerCommand.SeekForward
        return PlayerKeyHandling(
            consumed = true,
            command = command.takeIf { firstKeyDown || repeatable },
        )
    }
}
