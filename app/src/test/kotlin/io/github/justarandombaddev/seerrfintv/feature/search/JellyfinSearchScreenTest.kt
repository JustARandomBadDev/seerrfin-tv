package io.github.justarandombaddev.seerrfintv.feature.search

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class JellyfinSearchScreenTest {
    @Test
    fun `search field maps remote center and down without repeating on key up`() {
        assertEquals(SearchFieldKeyAction.ShowKeyboard, searchFieldKeyAction(Key.DirectionCenter, KeyEventType.KeyDown))
        assertEquals(SearchFieldKeyAction.ShowKeyboard, searchFieldKeyAction(Key.Enter, KeyEventType.KeyDown))
        assertEquals(SearchFieldKeyAction.MoveToSubmit, searchFieldKeyAction(Key.DirectionDown, KeyEventType.KeyDown))
        assertEquals(SearchFieldKeyAction.None, searchFieldKeyAction(Key.DirectionDown, KeyEventType.KeyUp))
    }
}
