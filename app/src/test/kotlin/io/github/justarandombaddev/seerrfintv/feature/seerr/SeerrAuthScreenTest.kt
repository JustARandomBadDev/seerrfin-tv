package io.github.justarandombaddev.seerrfintv.feature.seerr

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class SeerrAuthScreenTest {
    @Test
    fun `dpad down moves from Seerr address to connect action`() {
        assertEquals(
            SeerrUrlKeyAction.MoveToConnect,
            seerrUrlKeyAction(Key.DirectionDown, KeyEventType.KeyDown),
        )
    }

    @Test
    fun `center opens keyboard and key up is ignored`() {
        assertEquals(
            SeerrUrlKeyAction.ShowKeyboard,
            seerrUrlKeyAction(Key.DirectionCenter, KeyEventType.KeyDown),
        )
        assertEquals(
            SeerrUrlKeyAction.None,
            seerrUrlKeyAction(Key.DirectionDown, KeyEventType.KeyUp),
        )
    }
}
