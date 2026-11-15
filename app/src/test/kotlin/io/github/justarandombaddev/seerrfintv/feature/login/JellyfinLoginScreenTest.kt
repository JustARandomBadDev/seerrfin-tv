package io.github.justarandombaddev.seerrfintv.feature.login

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinLoginScreenTest {
    @Test
    fun `dpad down follows username password submit order`() {
        assertEquals(
            LoginFieldKeyAction.MoveToPassword,
            loginFieldKeyAction(LoginField.Username, Key.DirectionDown, KeyEventType.KeyDown),
        )
        assertEquals(
            LoginFieldKeyAction.MoveToSubmit,
            loginFieldKeyAction(LoginField.Password, Key.DirectionDown, KeyEventType.KeyDown),
        )
    }

    @Test
    fun `center and enter open keyboard while key up is ignored`() {
        assertEquals(
            LoginFieldKeyAction.ShowKeyboard,
            loginFieldKeyAction(LoginField.Username, Key.DirectionCenter, KeyEventType.KeyDown),
        )
        assertEquals(
            LoginFieldKeyAction.ShowKeyboard,
            loginFieldKeyAction(LoginField.Password, Key.Enter, KeyEventType.KeyDown),
        )
        assertEquals(
            LoginFieldKeyAction.None,
            loginFieldKeyAction(LoginField.Password, Key.DirectionDown, KeyEventType.KeyUp),
        )
    }
}
