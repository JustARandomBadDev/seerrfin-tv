package io.github.justarandombaddev.seerrfintv.feature.home

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinHomeRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinLibrariesScreenTest {
    @Test
    fun `home keeps only video libraries supported by the catalog`() {
        val libraries =
            listOf(
                JellyfinLibrary("movies", "Films", "MOVIES"),
                JellyfinLibrary("shows", "Séries", "TVSHOWS"),
                JellyfinLibrary("videos", "Vidéos", "HOMEVIDEOS"),
                JellyfinLibrary("music", "Musique", "MUSIC"),
                JellyfinLibrary("photos", "Photos", "PHOTOS"),
                JellyfinLibrary("unknown", "Dossiers", null),
            )

        assertEquals(
            listOf("movies", "shows", "videos"),
            supportedCatalogLibraries(libraries).map(JellyfinLibrary::id),
        )
    }

    @Test
    fun `stable home opens details for every visible catalog item`() {
        val movie = JellyfinCatalogItem("movie", "Movie", JellyfinCatalogItemType.Movie)
        val series = JellyfinCatalogItem("series", "Series", JellyfinCatalogItemType.Series)
        val library = JellyfinLibrary("movies", "Films", "MOVIES")
        val state =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.Success,
                libraries = listOf(library),
                homeRows = listOf(JellyfinHomeRow(library, listOf(movie, series))),
            )

        assertEquals(movie, catalogItemActivationRequest(state, movie))
        assertEquals(series, catalogItemActivationRequest(state, series))
        assertNull(
            catalogItemActivationRequest(
                state.copy(playbackStatus = PlaybackResolutionStatus.Loading),
                movie,
            ),
        )
        assertNull(
            catalogItemActivationRequest(
                state.copy(selectedLibraryId = "other"),
                movie,
            ),
        )
    }
}
