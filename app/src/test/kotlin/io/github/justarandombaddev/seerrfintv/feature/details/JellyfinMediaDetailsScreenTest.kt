package io.github.justarandombaddev.seerrfintv.feature.details

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.feature.home.JellyfinMediaDetailsUiState
import io.github.justarandombaddev.seerrfintv.feature.home.MediaDetailsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinMediaDetailsScreenTest {
    @Test
    fun `ready detail plays only its movie or listed episode`() {
        val movie = JellyfinCatalogItem("movie", "Movie", JellyfinCatalogItemType.Movie)
        val episode = JellyfinCatalogItem("episode", "Pilot", JellyfinCatalogItemType.Episode)
        val other = JellyfinCatalogItem("other", "Other", JellyfinCatalogItemType.Episode)

        assertEquals(
            movie,
            detailPlaybackRequest(
                JellyfinMediaDetailsUiState(status = MediaDetailsStatus.Ready, item = movie),
                movie,
            ),
        )
        assertEquals(
            episode,
            detailPlaybackRequest(
                JellyfinMediaDetailsUiState(
                    status = MediaDetailsStatus.Ready,
                    item = JellyfinCatalogItem("series", "Series", JellyfinCatalogItemType.Series),
                    episodes = listOf(episode),
                ),
                episode,
            ),
        )
        assertNull(
            detailPlaybackRequest(
                JellyfinMediaDetailsUiState(
                    status = MediaDetailsStatus.Ready,
                    item = JellyfinCatalogItem("series", "Series", JellyfinCatalogItemType.Series),
                    episodes = listOf(episode),
                ),
                other,
            ),
        )
        assertNull(
            detailPlaybackRequest(
                JellyfinMediaDetailsUiState(status = MediaDetailsStatus.Loading, item = movie),
                movie,
            ),
        )
    }
}
