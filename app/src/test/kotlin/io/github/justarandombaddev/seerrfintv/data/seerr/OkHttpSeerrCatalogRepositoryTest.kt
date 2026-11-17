package io.github.justarandombaddev.seerrfintv.data.seerr

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaType
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestFilter
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestSort
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSortDirection
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTimeWindow
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTvSeasonSelection
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrApiResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OkHttpSeerrCatalogRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OkHttpSeerrRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        repository = OkHttpSeerrRepository(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `movie discovery sends pagination and language with the active session`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{
                        "page":2,"totalPages":8,"totalResults":142,
                        "results":[{
                            "id":550,"mediaType":"movie","title":"Fight Club",
                            "overview":"An insomniac.","posterPath":"/poster.jpg",
                            "releaseDate":"1999-10-15","voteAverage":8.4,
                            "mediaInfo":{"id":91,"tmdbId":550,"status":5,"requests":[]}
                        }]
                    }""".trimIndent(),
                ),
            )

            val result = repository.discoverMovies(testSession(), page = 2, language = "fr")

            val page = (result as SeerrApiResult.Success).value
            assertEquals(2, page.page)
            assertEquals(8, page.totalPages)
            assertEquals(142, page.totalResults)
            assertEquals(550L, page.results.single().tmdbId)
            assertEquals("Fight Club", page.results.single().title)
            assertEquals(5, page.results.single().mediaInfo?.status?.code)
            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertEquals("/api/v1/discover/movies?page=2&language=fr", request.target)
            assertEquals("connect.sid=s%3Astored-cookie.signature", request.headers["Cookie"])
        }

    @Test
    fun `tv discovery and trending encode their documented media filters`() =
        runTest {
            server.enqueue(emptyPageResponse())
            server.enqueue(emptyPageResponse())

            repository.discoverSeries(testSession(), page = 3, language = "en")
            repository.discoverTrending(
                testSession(),
                page = 4,
                language = "fr",
                mediaType = SeerrMediaType.TV,
                timeWindow = SeerrTimeWindow.WEEK,
            )

            assertEquals("/api/v1/discover/tv?page=3&language=en", server.takeRequest().target)
            assertEquals(
                "/api/v1/discover/trending?page=4&language=fr&mediaType=tv&timeWindow=week",
                server.takeRequest().target,
            )
        }

    @Test
    fun `search parses movie tv and person results without confusing their media types`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{
                        "page":1,"totalPages":1,"totalResults":3,
                        "results":[
                            {"id":11,"mediaType":"movie","title":"Dune"},
                            {"id":22,"mediaType":"tv","name":"Dune: Prophecy","firstAirDate":"2024-11-17"},
                            {"id":33,"mediaType":"person","name":"Denis Villeneuve","profilePath":"/person.jpg"}
                        ]
                    }""".trimIndent(),
                ),
            )

            val result = repository.search(testSession(), query = "Dune & sand", page = 1, language = "fr")

            val items = (result as SeerrApiResult.Success).value.results
            assertEquals(listOf(SeerrMediaType.MOVIE, SeerrMediaType.TV, SeerrMediaType.PERSON), items.map { it.mediaType })
            assertEquals(listOf("Dune", "Dune: Prophecy", "Denis Villeneuve"), items.map { it.title })
            assertEquals(
                "/api/v1/search?query=Dune%20%26%20sand&page=1&language=fr",
                server.takeRequest().target,
            )
        }

    @Test
    fun `movie and tv details use TMDb ids and parse request state and seasons`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{
                        "id":550,"title":"Fight Club","overview":"An insomniac.","runtime":139,
                        "genres":[{"id":18,"name":"Drama"}],
                        "mediaInfo":{"id":91,"tmdbId":550,"status":2,"requests":[
                            {"id":71,"status":1,"is4k":false,"createdAt":"2026-11-17T10:00:00.000Z"}
                        ]}
                    }""".trimIndent(),
                ),
            )
            server.enqueue(
                jsonResponse(
                    """{
                        "id":1399,"name":"Game of Thrones","overview":"Nine families.",
                        "seasons":[{"id":3624,"name":"Season 1","seasonNumber":1,"episodeCount":10}],
                        "mediaInfo":{"id":92,"tmdbId":1399,"tvdbId":121361,"status":4,"requests":[]}
                    }""".trimIndent(),
                ),
            )

            val movie = repository.movieDetails(testSession(), tmdbId = 550, language = "fr")
            val series = repository.tvDetails(testSession(), tmdbId = 1399, language = "en")

            val movieValue = (movie as SeerrApiResult.Success).value
            assertEquals(139, movieValue.runtimeMinutes)
            assertEquals(1, movieValue.mediaInfo?.requests?.single()?.status?.code)
            val tvValue = (series as SeerrApiResult.Success).value
            assertEquals(121361L, tvValue.mediaInfo?.tvdbId)
            assertEquals(1, tvValue.seasons.single().seasonNumber)
            assertEquals("/api/v1/movie/550?language=fr", server.takeRequest().target)
            assertEquals("/api/v1/tv/1399?language=en", server.takeRequest().target)
        }

    @Test
    fun `request list sends pagination media state and sorting parameters`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{
                        "pageInfo":{"page":2,"pages":4,"results":63},
                        "results":[{"id":81,"status":2,"is4k":false,
                            "media":{"id":91,"tmdbId":550,"status":3}}]
                    }""".trimIndent(),
                ),
            )

            val result = repository.requests(
                session = testSession(),
                take = 20,
                skip = 20,
                filter = SeerrRequestFilter.APPROVED,
                sort = SeerrRequestSort.MODIFIED,
                sortDirection = SeerrSortDirection.ASCENDING,
                requestedBy = 7,
                mediaType = SeerrMediaType.MOVIE,
            )

            val page = (result as SeerrApiResult.Success).value
            assertEquals(4, page.pageInfo.pages)
            assertEquals(2, page.results.single().status.code)
            assertEquals(550L, page.results.single().media?.tmdbId)
            assertEquals(
                "/api/v1/request?take=20&skip=20&filter=approved&sort=modified&sortDirection=asc&requestedBy=7&mediaType=movie",
                server.takeRequest().target,
            )
        }

    @Test
    fun `movie request posts only the documented user scoped fields`() =
        runTest {
            server.enqueue(
                jsonResponse(
                    """{"id":101,"status":1,"is4k":false,"media":{"id":91,"tmdbId":550,"status":2}}""",
                    code = 201,
                ),
            )

            val result = repository.createMovieRequest(testSession(), tmdbId = 550, is4k = false)

            assertEquals(101L, (result as SeerrApiResult.Success).value.id)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/request", request.target)
            assertEquals(
                """{"mediaType":"movie","mediaId":550,"is4k":false}""",
                requireNotNull(request.body).utf8(),
            )
        }

    @Test
    fun `tv request serializes selected seasons and all seasons according to the contract`() =
        runTest {
            server.enqueue(jsonResponse("""{"id":102,"status":1}""", code = 201))
            server.enqueue(jsonResponse("""{"id":103,"status":1}""", code = 201))

            repository.createTvRequest(
                testSession(),
                tmdbId = 1399,
                seasons = SeerrTvSeasonSelection.Selected(listOf(1, 3)),
                is4k = true,
            )
            repository.createTvRequest(
                testSession(),
                tmdbId = 1399,
                seasons = SeerrTvSeasonSelection.All,
                is4k = false,
            )

            assertEquals(
                """{"mediaType":"tv","mediaId":1399,"seasons":[1,3],"is4k":true}""",
                requireNotNull(server.takeRequest().body).utf8(),
            )
            assertEquals(
                """{"mediaType":"tv","mediaId":1399,"seasons":"all","is4k":false}""",
                requireNotNull(server.takeRequest().body).utf8(),
            )
        }

    @Test
    fun `catalog calls distinguish expired forbidden missing server and malformed responses`() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())
            server.enqueue(MockResponse.Builder().code(403).build())
            server.enqueue(MockResponse.Builder().code(404).build())
            server.enqueue(MockResponse.Builder().code(503).build())
            server.enqueue(jsonResponse("not-json"))

            assertEquals(SeerrApiResult.SessionExpired, repository.discoverMovies(testSession()))
            assertEquals(SeerrApiResult.PermissionDenied, repository.discoverMovies(testSession()))
            assertEquals(SeerrApiResult.NotFound, repository.movieDetails(testSession(), 999))
            assertEquals(SeerrApiResult.ServerRejected, repository.discoverSeries(testSession()))
            assertEquals(SeerrApiResult.InvalidResponse, repository.search(testSession(), "Dune"))
        }

    @Test
    fun `catalog call rejects an expired session without contacting the server`() =
        runTest {
            val expired = testSession().copy(expiresAtEpochMillis = 1L)

            val result = repository.discoverMovies(expired)

            assertEquals(SeerrApiResult.SessionExpired, result)
            assertEquals(0, server.requestCount)
        }

    private fun emptyPageResponse(): MockResponse =
        jsonResponse("""{"page":1,"totalPages":0,"totalResults":0,"results":[]}""")

    private fun jsonResponse(body: String, code: Int = 200): MockResponse =
        MockResponse.Builder()
            .code(code)
            .addHeader("Content-Type", "application/json")
            .body(body)
            .build()

    private fun testSession() =
        SeerrSession(
            serverUrl = server.url("/").toString(),
            cookieName = "connect.sid",
            cookieValue = "s%3Astored-cookie.signature",
            expiresAtEpochMillis = System.currentTimeMillis() + 60_000L,
        )
}
