package io.github.justarandombaddev.seerrfintv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class BootstrapStateTest {
    @Test
    fun `activating an action exposes visible confirmation`() {
        val state = BootstrapState()

        val activated = state.activate(BootstrapAction.FocusTwo)

        assertEquals(BootstrapAction.FocusTwo, activated.activeAction)
        assertEquals("Focus 2 activé", activated.status)
    }
}
