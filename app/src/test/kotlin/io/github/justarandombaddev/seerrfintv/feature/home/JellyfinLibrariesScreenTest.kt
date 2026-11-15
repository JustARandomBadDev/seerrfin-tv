package io.github.justarandombaddev.seerrfintv.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinLibrariesScreenTest {
    @Test
    fun `stable library content accepts direct activation`() {
        val state =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.Success,
                playbackStatus = PlaybackResolutionStatus.Idle,
            )

        assertEquals("library-id", libraryActivationRequest(state, "library-id"))
    }

    @Test
    fun `activation is suppressed during resolution or outside library content`() {
        assertNull(
            libraryActivationRequest(
                JellyfinLibrariesUiState(
                    status = LibrariesStatus.Success,
                    playbackStatus = PlaybackResolutionStatus.Loading,
                ),
                "library-id",
            ),
        )
        assertNull(
            libraryActivationRequest(
                JellyfinLibrariesUiState(status = LibrariesStatus.Loading),
                "library-id",
            ),
        )
    }
}
