package io.github.justarandombaddev.seerrfintv.feature.connect

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerConnectionScreenTest {
    @Test
    fun `dpad down moves from server field to connect action`() {
        assertEquals(
            ServerUrlKeyAction.MoveToConnect,
            serverUrlKeyAction(Key.DirectionDown, KeyEventType.KeyDown),
        )
    }

    @Test
    fun `center opens keyboard and key up is ignored`() {
        assertEquals(
            ServerUrlKeyAction.ShowKeyboard,
            serverUrlKeyAction(Key.DirectionCenter, KeyEventType.KeyDown),
        )
        assertEquals(
            ServerUrlKeyAction.None,
            serverUrlKeyAction(Key.DirectionDown, KeyEventType.KeyUp),
        )
    }
}
