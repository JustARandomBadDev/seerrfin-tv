package io.github.justarandombaddev.seerrfintv.data.jellyfin

import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinHomeResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackReportResult
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinHomeRow
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReportKind
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import io.github.justarandombaddev.seerrfintv.playback.PlaybackSourceCandidate
import java.util.concurrent.CancellationException
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SdkJellyfinRepositoryTest {
    @Test
    fun `home rows query supported libraries with bounded media types and map items`() =
        runTest {
            val requests = mutableListOf<SdkHomeItemsRequest>()
            val repository =
                repositoryWithHome { request ->
                    requests += request
                    listOf(
                        SdkCatalogItem(
                            id = "00000000-0000-0000-0000-000000000100",
                            name = if (request.itemTypes == listOf(BaseItemKind.MOVIE)) "Example Movie" else "Example Series",
                            type = request.itemTypes.single().name,
                            productionYear = 2026,
                            playbackPositionTicks = 120_000,
                        ),
                    )
                }
            val movies = JellyfinLibrary(TEST_LIBRARY_ID, "Films", "MOVIES")
            val shows = JellyfinLibrary("00000000-0000-0000-0000-000000000011", "Séries", "TVSHOWS")
            val music = JellyfinLibrary("00000000-0000-0000-0000-000000000012", "Musique", "MUSIC")

            val result = repository.fetchHomeRows(TEST_SERVER_URL, testSession(), listOf(movies, shows, music))

            assertEquals(
                listOf(
                    HomeRequestSummary(TEST_LIBRARY_ID, listOf(BaseItemKind.MOVIE), 20),
                    HomeRequestSummary(shows.id, listOf(BaseItemKind.SERIES), 20),
                ),
                requests.map { HomeRequestSummary(it.libraryId.toString(), it.itemTypes, it.limit) },
            )
            assertEquals(
                JellyfinHomeResult.Success(
                    listOf(
                        JellyfinHomeRow(
                            movies,
                            listOf(
                                JellyfinCatalogItem(
                                    id = "00000000-0000-0000-0000-000000000100",
                                    name = "Example Movie",
                                    type = JellyfinCatalogItemType.Movie,
                                    productionYear = 2026,
                                    playbackPositionTicks = 120_000,
                                ),
                            ),
                        ),
                        JellyfinHomeRow(
                            shows,
                            listOf(
                                JellyfinCatalogItem(
                                    id = "00000000-0000-0000-0000-000000000100",
                                    name = "Example Series",
                                    type = JellyfinCatalogItemType.Series,
                                    productionYear = 2026,
                                    playbackPositionTicks = 120_000,
                                ),
                            ),
                        ),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `selected catalog movie resolves that exact item with its resume position`() =
        runTest {
            val requests = mutableListOf<SdkPlaybackInfoRequest>()
            val repository =
                repositoryWithPlayback(
                    itemProvider = { error("Library scan must not run for a selected catalog item") },
                    playbackProvider = { request ->
                        requests += request
                        playbackInfo(
                            playbackCandidate(
                                supportsDirectPlay = true,
                                directPlayUrl = "https://media.example.test/video.mp4",
                            ),
                        )
                    },
                )
            val item =
                JellyfinCatalogItem(
                    id = "00000000-0000-0000-0000-000000000100",
                    name = "Chosen Movie",
                    type = JellyfinCatalogItemType.Movie,
                    playbackPositionTicks = 123_450_000,
                )

            val result = repository.resolvePlaybackItem(TEST_SERVER_URL, testSession(), item)

            assertEquals(JellyfinPlaybackResolutionResult.Success::class, result::class)
            assertEquals(
                SdkPlayableItem(
                    id = item.id,
                    name = item.name,
                    type = "MOVIE",
                    playbackPositionTicks = 123_450_000,
                ),
                requests.single().item,
            )
        }

    @Test
    fun `empty supported library is omitted from home rows`() =
        runTest {
            val repository = repositoryWithHome { emptyList() }

            val result =
                repository.fetchHomeRows(
                    TEST_SERVER_URL,
                    testSession(),
                    listOf(JellyfinLibrary(TEST_LIBRARY_ID, "Films", "MOVIES")),
                )

            assertEquals(JellyfinHomeResult.Success(emptyList()), result)
        }
    @Test
    fun `valid Jellyfin identity is accepted`() =
        runTest {
            val repository =
                repositoryWithProbe {
                    PublicSystemInfo(
                        version = "12.4.0",
                        productName = "Jellyfin Server",
                    )
                }

            assertEquals(
                JellyfinConnectionResult.Connected("https://media.example.test/"),
                repository.connect("https://media.example.test/"),
            )
        }

    @Test
    fun `empty public system information is rejected`() =
        runTest {
            val repository = repositoryWithProbe { PublicSystemInfo() }

            assertEquals(
                JellyfinConnectionResult.InvalidResponse,
                repository.connect("https://not-jellyfin.example.test/"),
            )
        }

    @Test
    fun `valid authentication response becomes app session`() =
        runTest {
            val repository =
                repositoryWithAuthenticator { serverUrl, username, password ->
                    assertEquals("https://media.example.test/", serverUrl)
                    assertEquals("alice", username)
                    assertEquals("", password)
                    SdkAuthenticationSession(
                        accessToken = "test-access-token",
                        userId = "00000000-0000-0000-0000-000000000001",
                        serverId = "test-server-id",
                        displayName = "Alice",
                    )
                }

            val result =
                repository.authenticate(
                    serverUrl = "https://media.example.test/",
                    username = "alice",
                    password = "",
                )

            assertEquals(
                JellyfinAuthenticationResult.Authenticated(
                    session =
                        io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession(
                            accessToken = "test-access-token",
                            userId = "00000000-0000-0000-0000-000000000001",
                            serverId = "test-server-id",
                        ),
                    displayName = "Alice",
                ),
                result,
            )
        }

    @Test
    fun `incomplete authentication response is rejected`() =
        runTest {
            val incompleteResponses =
                listOf(
                    SdkAuthenticationSession("", "user-id", "server-id", "Alice"),
                    SdkAuthenticationSession("token", "", "server-id", "Alice"),
                    SdkAuthenticationSession("token", "user-id", "", "Alice"),
                    SdkAuthenticationSession("token", "user-id", "server-id", ""),
                )

            incompleteResponses.forEach { response ->
                val repository = repositoryWithAuthenticator { _, _, _ -> response }

                assertEquals(
                    JellyfinAuthenticationResult.InvalidResponse,
                    repository.authenticate("https://media.example.test/", "alice", "password"),
                )
            }
        }

    @Test
    fun `authentication failures map to app errors`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinAuthenticationResult.InvalidCredentials,
                    TimeoutException() to JellyfinAuthenticationResult.Unreachable,
                    SecureConnectionException() to JellyfinAuthenticationResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinAuthenticationResult.ServerRejected,
                    InvalidContentException() to JellyfinAuthenticationResult.InvalidResponse,
                    ApiClientException() to JellyfinAuthenticationResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository = repositoryWithAuthenticator { _, _, _ -> throw failure }

                assertEquals(
                    expected,
                    repository.authenticate("https://media.example.test/", "alice", "password"),
                )
            }
        }

    @Test
    fun `authentication cancellation is rethrown`() =
        runTest {
            val repository = repositoryWithAuthenticator { _, _, _ -> throw CancellationException() }

            try {
                repository.authenticate("https://media.example.test/", "alice", "password")
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a UI error.
            }
        }

    @Test
    fun `restored session fetches and maps accessible user views`() =
        runTest {
            val calls = mutableListOf<LibraryCall>()
            val repository =
                repositoryWithLibraries { serverUrl, accessToken, userId ->
                    calls += LibraryCall(serverUrl, accessToken, userId)
                    listOf(
                        SdkJellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000010",
                            name = "Films",
                            collectionType = "MOVIES",
                        ),
                        SdkJellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000011",
                            name = "Séries",
                            collectionType = "TVSHOWS",
                        ),
                    )
                }
            val session = testSession()

            val result = repository.fetchLibraries("https://media.example.test/", session)

            assertEquals(
                listOf(
                    LibraryCall(
                        serverUrl = "https://media.example.test/",
                        accessToken = "test-access-token",
                        userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    ),
                ),
                calls,
            )
            assertEquals(
                JellyfinLibrariesResult.Success(
                    listOf(
                        JellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000010",
                            name = "Films",
                            collectionType = "MOVIES",
                        ),
                        JellyfinLibrary(
                            id = "00000000-0000-0000-0000-000000000011",
                            name = "Séries",
                            collectionType = "TVSHOWS",
                        ),
                    ),
                ),
                result,
            )
        }

    @Test
    fun `invalid restored session is rejected before SDK call`() =
        runTest {
            var calls = 0
            val repository =
                repositoryWithLibraries { _, _, _ ->
                    calls += 1
                    emptyList()
                }

            val result =
                repository.fetchLibraries(
                    "https://media.example.test/",
                    testSession().copy(userId = "not-a-uuid"),
                )

            assertEquals(JellyfinLibrariesResult.InvalidSession, result)
            assertEquals(0, calls)
        }

    @Test
    fun `incomplete user view is rejected`() =
        runTest {
            val repository =
                repositoryWithLibraries { _, _, _ ->
                    listOf(SdkJellyfinLibrary(id = "", name = "Films", collectionType = "MOVIES"))
                }

            assertEquals(
                JellyfinLibrariesResult.InvalidResponse,
                repository.fetchLibraries("https://media.example.test/", testSession()),
            )
        }

    @Test
    fun `library failures distinguish revoked session from retryable errors`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinLibrariesResult.InvalidSession,
                    TimeoutException() to JellyfinLibrariesResult.Unreachable,
                    SecureConnectionException() to JellyfinLibrariesResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinLibrariesResult.ServerRejected,
                    InvalidContentException() to JellyfinLibrariesResult.InvalidResponse,
                    ApiClientException() to JellyfinLibrariesResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository = repositoryWithLibraries { _, _, _ -> throw failure }

                assertEquals(
                    expected,
                    repository.fetchLibraries("https://media.example.test/", testSession()),
                )
            }
        }

    @Test
    fun `library cancellation is rethrown`() =
        runTest {
            val repository = repositoryWithLibraries { _, _, _ -> throw CancellationException() }

            try {
                repository.fetchLibraries("https://media.example.test/", testSession())
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a UI error.
            }
        }

    @Test
    fun `recursive paginated search skips unusable candidates and resolves a later page`() =
        runTest {
            val itemRequests = mutableListOf<SdkPlayableItemsRequest>()
            val playbackRequests = mutableListOf<SdkPlaybackInfoRequest>()
            val pages =
                mapOf(
                    0 to
                        SdkPlayableItemPage(
                            items =
                                listOf(
                                    playableItem("00000000-0000-0000-0000-000000000100", "Unavailable A"),
                                    playableItem("00000000-0000-0000-0000-000000000101", "Unavailable B"),
                                ),
                            totalRecordCount = 3,
                        ),
                    2 to
                        SdkPlayableItemPage(
                            items = listOf(playableItem("00000000-0000-0000-0000-000000000102", "Playable")),
                            totalRecordCount = 3,
                        ),
                )
            val repository =
                repositoryWithPlayback(
                    pageSize = 2,
                    itemProvider = { request ->
                        itemRequests += request
                        pages.getValue(request.startIndex)
                    },
                    playbackProvider = { request ->
                        playbackRequests += request
                        if (request.item.id.endsWith("102")) {
                            playbackInfo(
                                candidate =
                                    playbackCandidate(
                                        supportsDirectPlay = true,
                                        directPlayUrl = "https://media.example.test/video.mp4",
                                    ),
                            )
                        } else {
                            playbackInfo(candidate = playbackCandidate())
                        }
                    },
                )

            val result =
                repository.resolvePlaybackSource(
                    serverUrl = "https://media.example.test/",
                    session = testSession(),
                    libraryId = "00000000-0000-0000-0000-000000000010",
                )

            assertEquals(listOf(0, 2), itemRequests.map(SdkPlayableItemsRequest::startIndex))
            assertEquals(listOf(2, 2), itemRequests.map(SdkPlayableItemsRequest::limit))
            assertEquals(true, itemRequests.all(SdkPlayableItemsRequest::recursive))
            assertEquals(3, playbackRequests.size)
            val success = result as JellyfinPlaybackResolutionResult.Success
            assertEquals("Playable", success.source.item.name)
            assertEquals(PlaybackMethod.DIRECT_PLAY, success.source.method)
        }

    @Test
    fun `resolved source keeps the Jellyfin resume position`() =
        runTest {
            val repository =
                repositoryWithPlayback(
                    itemProvider = {
                        SdkPlayableItemPage(
                            items =
                                listOf(
                                    playableItem(
                                        id = "00000000-0000-0000-0000-000000000100",
                                        name = "Resumable",
                                        playbackPositionTicks = 123_450_000L,
                                    ),
                                ),
                            totalRecordCount = 1,
                        )
                    },
                    playbackProvider = {
                        playbackInfo(
                            candidate =
                                playbackCandidate(
                                    supportsDirectPlay = true,
                                    directPlayUrl = "https://media.example.test/video.mp4",
                                ),
                        )
                    },
                )

            val result = repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID)

            val success = result as JellyfinPlaybackResolutionResult.Success
            assertEquals(12_345L, success.source.item.resumePositionMs)
        }

    @Test
    fun `playback negotiation includes the Jellyfin resume position for transcoding`() {
        val request =
            SdkPlaybackInfoRequest(
                serverUrl = TEST_SERVER_URL,
                accessToken = "test-access-token",
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                item =
                    playableItem(
                        id = "00000000-0000-0000-0000-000000000100",
                        name = "Resumable",
                        playbackPositionTicks = 123_450_000L,
                    ),
            )

        val payload =
            request.toPlaybackInfoDto(
                deviceProfile = buildDeviceProfile { name = "Test profile" },
            )

        assertEquals(123_450_000L, payload.startTimeTicks)
        assertEquals(request.userId, payload.userId)
        assertEquals(true, payload.enableDirectPlay)
        assertEquals(true, payload.enableDirectStream)
        assertEquals(true, payload.enableTranscoding)
    }

    @Test
    fun `playback report maps app state to Jellyfin start payload`() {
        val payload =
            playbackReport(kind = JellyfinPlaybackReportKind.Start)
                .toPlaybackStartInfo()

        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000100"), payload.itemId)
        assertEquals("media-source", payload.mediaSourceId)
        assertEquals("play-session", payload.playSessionId)
        assertEquals(123_450_000L, payload.positionTicks)
        assertEquals(org.jellyfin.sdk.model.api.PlayMethod.TRANSCODE, payload.playMethod)
        assertEquals(false, payload.isPaused)
        assertEquals(true, payload.canSeek)
        assertEquals(org.jellyfin.sdk.model.api.RepeatMode.REPEAT_NONE, payload.repeatMode)
        assertEquals(org.jellyfin.sdk.model.api.PlaybackOrder.DEFAULT, payload.playbackOrder)
    }

    @Test
    fun `playback report maps pause and failure to progress and stop payloads`() {
        val progress =
            playbackReport(
                kind = JellyfinPlaybackReportKind.Progress,
                isPaused = true,
            ).toPlaybackProgressInfo()
        val stop =
            playbackReport(
                kind = JellyfinPlaybackReportKind.Stop,
                failed = true,
            ).toPlaybackStopInfo()

        assertEquals(true, progress.isPaused)
        assertEquals(123_450_000L, progress.positionTicks)
        assertEquals("media-source", progress.mediaSourceId)
        assertEquals(true, stop.failed)
        assertEquals(123_450_000L, stop.positionTicks)
        assertEquals("play-session", stop.playSessionId)
    }

    @Test
    fun `valid playback report is sent with authenticated context`() =
        runTest {
            val requests = mutableListOf<SdkPlaybackReportRequest>()
            val repository =
                repositoryWithReporting { request ->
                    requests += request
                }
            val report = playbackReport(JellyfinPlaybackReportKind.Progress)

            val result = repository.reportPlayback(TEST_SERVER_URL, testSession(), report)

            assertEquals(JellyfinPlaybackReportResult.Success, result)
            assertEquals(1, requests.size)
            assertEquals(TEST_SERVER_URL, requests.single().serverUrl)
            assertEquals("test-access-token", requests.single().accessToken)
            assertEquals(report, requests.single().report)
            assertEquals("SdkPlaybackReportRequest(<redacted>)", requests.single().toString())
        }

    @Test
    fun `playback reporting failures map without exposing SDK errors`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinPlaybackReportResult.InvalidSession,
                    TimeoutException() to JellyfinPlaybackReportResult.Unreachable,
                    SecureConnectionException() to JellyfinPlaybackReportResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinPlaybackReportResult.ServerRejected,
                    InvalidContentException() to JellyfinPlaybackReportResult.InvalidResponse,
                    ApiClientException() to JellyfinPlaybackReportResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository = repositoryWithReporting { throw failure }

                assertEquals(
                    expected,
                    repository.reportPlayback(
                        TEST_SERVER_URL,
                        testSession(),
                        playbackReport(JellyfinPlaybackReportKind.Progress),
                    ),
                )
            }
        }

    @Test
    fun `playback reporting cancellation is rethrown`() =
        runTest {
            val repository = repositoryWithReporting { throw CancellationException() }

            try {
                repository.reportPlayback(
                    TEST_SERVER_URL,
                    testSession(),
                    playbackReport(JellyfinPlaybackReportKind.Progress),
                )
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a reporting result.
            }
        }

    @Test
    fun `empty page terminates recursive search without another request`() =
        runTest {
            var pageCalls = 0
            var playbackCalls = 0
            val repository =
                repositoryWithPlayback(
                    itemProvider = {
                        pageCalls += 1
                        SdkPlayableItemPage(emptyList(), totalRecordCount = 20)
                    },
                    playbackProvider = {
                        playbackCalls += 1
                        playbackInfo()
                    },
                )

            val result = repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID)

            assertEquals(JellyfinPlaybackResolutionResult.Empty, result)
            assertEquals(1, pageCalls)
            assertEquals(0, playbackCalls)
        }

    @Test
    fun `all candidates without an authorized usable method produce empty result`() =
        runTest {
            val repository =
                repositoryWithPlayback(
                    itemProvider = {
                        SdkPlayableItemPage(
                            listOf(playableItem("00000000-0000-0000-0000-000000000100", "Unavailable")),
                            totalRecordCount = 1,
                        )
                    },
                    playbackProvider = {
                        playbackInfo(
                            candidate =
                                playbackCandidate(
                                    supportsDirectStream = true,
                                    transcodingUrl = null,
                                ),
                        )
                    },
                )

            assertEquals(
                JellyfinPlaybackResolutionResult.Empty,
                repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID),
            )
        }

    @Test
    fun `malformed library or item identity is rejected as invalid response`() =
        runTest {
            var pageCalls = 0
            val invalidLibraryRepository =
                repositoryWithPlayback(
                    itemProvider = {
                        pageCalls += 1
                        SdkPlayableItemPage(emptyList(), 0)
                    },
                )

            assertEquals(
                JellyfinPlaybackResolutionResult.InvalidResponse,
                invalidLibraryRepository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), "not-a-uuid"),
            )
            assertEquals(0, pageCalls)

            val invalidItemRepository =
                repositoryWithPlayback(
                    itemProvider = {
                        SdkPlayableItemPage(
                            listOf(playableItem("not-a-uuid", "Malformed")),
                            totalRecordCount = 1,
                        )
                    },
                )
            assertEquals(
                JellyfinPlaybackResolutionResult.InvalidResponse,
                invalidItemRepository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID),
            )
        }

    @Test
    fun `playback lookup failures map to app results`() =
        runTest {
            val cases =
                listOf(
                    InvalidStatusException(401) to JellyfinPlaybackResolutionResult.InvalidSession,
                    TimeoutException() to JellyfinPlaybackResolutionResult.Unreachable,
                    SecureConnectionException() to JellyfinPlaybackResolutionResult.SecureConnectionFailed,
                    InvalidStatusException(503) to JellyfinPlaybackResolutionResult.ServerRejected,
                    InvalidContentException() to JellyfinPlaybackResolutionResult.InvalidResponse,
                    ApiClientException() to JellyfinPlaybackResolutionResult.UnexpectedFailure,
                )

            cases.forEach { (failure, expected) ->
                val repository =
                    repositoryWithPlayback(
                        itemProvider = { oneItemPage() },
                        playbackProvider = { throw failure },
                    )

                assertEquals(
                    expected,
                    repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID),
                )
            }
        }

    @Test
    fun `item page unauthorized response maps to invalid session`() =
        runTest {
            val repository =
                repositoryWithPlayback(
                    itemProvider = { throw InvalidStatusException(401) },
                )

            assertEquals(
                JellyfinPlaybackResolutionResult.InvalidSession,
                repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID),
            )
        }

    @Test
    fun `playback resolution cancellation is rethrown`() =
        runTest {
            val repository =
                repositoryWithPlayback(
                    itemProvider = { oneItemPage() },
                    playbackProvider = { throw CancellationException() },
                )

            try {
                repository.resolvePlaybackSource(TEST_SERVER_URL, testSession(), TEST_LIBRARY_ID)
                fail("CancellationException expected")
            } catch (_: CancellationException) {
                // Expected: structured concurrency cancellation must not become a UI error.
            }
        }

    private fun repositoryWithAuthenticator(
        authenticator: JellyfinUserAuthenticator,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = authenticator,
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
            playableItemProvider = JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
            playbackInfoProvider = JellyfinPlaybackInfoProvider { playbackInfo() },
        )

    private fun repositoryWithProbe(
        probe: JellyfinServerProbe,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = probe,
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by connection tests") },
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
            playableItemProvider = JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
            playbackInfoProvider = JellyfinPlaybackInfoProvider { playbackInfo() },
        )

    private fun repositoryWithLibraries(
        provider: JellyfinLibraryProvider,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by library tests") },
            libraryProvider = provider,
            playableItemProvider = JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
            playbackInfoProvider = JellyfinPlaybackInfoProvider { playbackInfo() },
        )

    private fun repositoryWithHome(
        provider: JellyfinHomeItemProvider,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by home tests") },
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
            playableItemProvider = JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
            playbackInfoProvider = JellyfinPlaybackInfoProvider { playbackInfo() },
            homeItemProvider = provider,
        )

    private fun repositoryWithPlayback(
        pageSize: Int = 50,
        itemProvider: JellyfinPlayableItemProvider =
            JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
        playbackProvider: JellyfinPlaybackInfoProvider =
            JellyfinPlaybackInfoProvider { playbackInfo() },
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by playback tests") },
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
            playableItemProvider = itemProvider,
            playbackInfoProvider = playbackProvider,
            playbackPageSize = pageSize,
        )

    private fun repositoryWithReporting(
        reportProvider: JellyfinPlaybackReportProvider,
    ): SdkJellyfinRepository =
        SdkJellyfinRepository(
            probe = JellyfinServerProbe { PublicSystemInfo() },
            authenticator = JellyfinUserAuthenticator { _, _, _ -> error("Not used by reporting tests") },
            libraryProvider = JellyfinLibraryProvider { _, _, _ -> emptyList() },
            playableItemProvider = JellyfinPlayableItemProvider { SdkPlayableItemPage(emptyList(), 0) },
            playbackInfoProvider = JellyfinPlaybackInfoProvider { playbackInfo() },
            playbackReportProvider = reportProvider,
        )

    private fun testSession() =
        JellyfinSession(
            accessToken = "test-access-token",
            userId = "00000000-0000-0000-0000-000000000001",
            serverId = "test-server-id",
        )

    private companion object {
        const val TEST_SERVER_URL = "https://media.example.test/"
        const val TEST_LIBRARY_ID = "00000000-0000-0000-0000-000000000010"

        fun playableItem(
            id: String,
            name: String,
            playbackPositionTicks: Long = 0,
        ) =
            SdkPlayableItem(
                id = id,
                name = name,
                type = "MOVIE",
                playbackPositionTicks = playbackPositionTicks,
            )

        fun playbackReport(
            kind: JellyfinPlaybackReportKind,
            isPaused: Boolean = false,
            failed: Boolean = false,
        ) =
            JellyfinPlaybackReport(
                kind = kind,
                itemId = "00000000-0000-0000-0000-000000000100",
                mediaSourceId = "media-source",
                playSessionId = "play-session",
                method = PlaybackMethod.TRANSCODE,
                positionMs = 12_345L,
                isPaused = isPaused,
                isSeekable = true,
                failed = failed,
            )

        fun playbackCandidate(
            supportsDirectPlay: Boolean = false,
            supportsDirectStream: Boolean = false,
            supportsTranscoding: Boolean = false,
            directPlayUrl: String? = null,
            transcodingUrl: String? = null,
        ) =
            PlaybackSourceCandidate(
                mediaSourceId = "media-source",
                container = "mp4",
                transcodingContainer = "ts",
                supportsDirectPlay = supportsDirectPlay,
                supportsDirectStream = supportsDirectStream,
                supportsTranscoding = supportsTranscoding,
                directPlayUrl = directPlayUrl,
                transcodingUrl = transcodingUrl,
                requiredHttpHeaders = emptyMap(),
            )

        fun playbackInfo(candidate: PlaybackSourceCandidate? = null) =
            SdkPlaybackInfo(
                playSessionId = "play-session",
                errorCode = null,
                sources = listOfNotNull(candidate),
            )

        fun oneItemPage() =
            SdkPlayableItemPage(
                listOf(playableItem("00000000-0000-0000-0000-000000000100", "Movie")),
                totalRecordCount = 1,
            )
    }
}

private data class LibraryCall(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
)

private data class HomeRequestSummary(
    val libraryId: String,
    val itemTypes: List<BaseItemKind>,
    val limit: Int,
)
