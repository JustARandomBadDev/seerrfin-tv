package io.github.justarandombaddev.seerrfintv.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrPermissionsTest {
    @Test
    fun `general request permission allows movie and series requests`() {
        val permissions = SeerrPermissions(32L)

        assertTrue(permissions.canRequestMovies)
        assertTrue(permissions.canRequestSeries)
        assertFalse(permissions.canRequest4kMovies)
        assertFalse(permissions.canRequest4kSeries)
    }

    @Test
    fun `media-specific permissions remain independent`() {
        val permissions = SeerrPermissions(262_144L or 4_096L)

        assertTrue(permissions.canRequestMovies)
        assertFalse(permissions.canRequestSeries)
        assertFalse(permissions.canRequest4kMovies)
        assertTrue(permissions.canRequest4kSeries)
    }

    @Test
    fun `administrator permission grants every request capability`() {
        val permissions = SeerrPermissions(2L)

        assertTrue(permissions.canRequestMovies)
        assertTrue(permissions.canRequestSeries)
        assertTrue(permissions.canRequest4kMovies)
        assertTrue(permissions.canRequest4kSeries)
    }

    @Test
    fun `unrelated permission does not grant request capability`() {
        val permissions = SeerrPermissions(16_384L)

        assertFalse(permissions.canRequestMovies)
        assertFalse(permissions.canRequestSeries)
        assertFalse(permissions.canRequest4kMovies)
        assertFalse(permissions.canRequest4kSeries)
    }

    @Test
    fun `user string representation redacts identity`() {
        val user = SeerrUser(7L, "Private User", SeerrPermissions(32L))

        val rendered = user.toString()

        assertFalse(rendered.contains("Private User"))
        assertFalse(rendered.contains("7"))
        assertTrue(rendered.contains("<redacted>"))
    }
}
