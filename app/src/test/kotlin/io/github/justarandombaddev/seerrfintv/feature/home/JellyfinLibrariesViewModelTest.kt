package io.github.justarandombaddev.seerrfintv.feature.home

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinHomeRow
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReportKind
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSeason
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinHomeResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinEpisodesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinSeriesDetailsResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackReportResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinLibrariesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `opening a movie shows its detail without resolving playback`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            val movie = testCatalogItem()

            viewModel.openDetails(movie)

            assertEquals(MediaDetailsStatus.Ready, viewModel.uiState.value.details.status)
            assertEquals(movie, viewModel.uiState.value.details.item)
            assertEquals(emptyList<PlaybackItemResolveCall>(), repository.resolveItemCalls)
        }

    @Test
    fun `series detail loads seasons switches season and back preserves home filter`() =
        runTest(mainDispatcherRule.dispatcher) {
            val shows = JellyfinLibrary("00000000-0000-0000-0000-000000000011", "Séries", "TVSHOWS")
            val series =
                JellyfinCatalogItem(
                    id = "00000000-0000-0000-0000-000000000200",
                    name = "Example Series",
                    type = JellyfinCatalogItemType.Series,
                )
            val season1 = JellyfinSeason("00000000-0000-0000-0000-000000000201", "Season 1", 1)
            val season2 = JellyfinSeason("00000000-0000-0000-0000-000000000202", "Season 2", 2)
            val episode2 = testEpisode(id = "00000000-0000-0000-0000-000000000302", season = 2)
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(shows)),
                    homeResult = JellyfinHomeResult.Success(listOf(JellyfinHomeRow(shows, listOf(series)))),
                    seriesResult =
                        JellyfinSeriesDetailsResult.Success(
                            seasons = listOf(season1, season2),
                            selectedSeasonId = season1.id,
                            episodes = listOf(testEpisode()),
                        ),
                    episodesResult = JellyfinEpisodesResult.Success(listOf(episode2)),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            viewModel.selectLibrary(shows.id)

            viewModel.openDetails(series)
            advanceUntilIdle()
            viewModel.selectSeason(season2.id)
            advanceUntilIdle()

            assertEquals(MediaDetailsStatus.Ready, viewModel.uiState.value.details.status)
            assertEquals(season2.id, viewModel.uiState.value.details.selectedSeasonId)
            assertEquals(listOf(episode2), viewModel.uiState.value.details.episodes)
            assertEquals(1, repository.seriesCalls.size)
            assertEquals(listOf(SeasonEpisodesCall(SERVER_URL, testSession(), series.id, season2.id)), repository.episodeCalls)

            viewModel.closeDetails()

            assertEquals(MediaDetailsStatus.Closed, viewModel.uiState.value.details.status)
            assertEquals(shows.id, viewModel.uiState.value.selectedLibraryId)
            assertEquals(listOf(series), viewModel.uiState.value.visibleHomeRows.single().items)
        }

    @Test
    fun `back during series loading ignores the late response`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val series =
                JellyfinCatalogItem(
                    id = "00000000-0000-0000-0000-000000000200",
                    name = "Example Series",
                    type = JellyfinCatalogItemType.Series,
                )
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    homeResult = JellyfinHomeResult.Success(listOf(JellyfinHomeRow(testLibrary(), listOf(series)))),
                    seriesResult =
                        JellyfinSeriesDetailsResult.Success(
                            seasons = listOf(JellyfinSeason("00000000-0000-0000-0000-000000000201", "Season 1", 1)),
                            selectedSeasonId = "00000000-0000-0000-0000-000000000201",
                            episodes = listOf(testEpisode()),
                        ),
                    beforeSeriesResult = { gate.await() },
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.openDetails(series)
            runCurrent()
            viewModel.closeDetails()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(MediaDetailsStatus.Closed, viewModel.uiState.value.details.status)
            assertNull(viewModel.uiState.value.details.item)
        }

    @Test
    fun `episode activation reuses exact item playback pipeline`() =
        runTest(mainDispatcherRule.dispatcher) {
            val series =
                JellyfinCatalogItem(
                    id = "00000000-0000-0000-0000-000000000200",
                    name = "Example Series",
                    type = JellyfinCatalogItemType.Series,
                )
            val episode = testEpisode()
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    homeResult = JellyfinHomeResult.Success(listOf(JellyfinHomeRow(testLibrary(), listOf(series)))),
                    seriesResult =
                        JellyfinSeriesDetailsResult.Success(
                            seasons = listOf(JellyfinSeason("00000000-0000-0000-0000-000000000201", "Season 1", 1)),
                            selectedSeasonId = "00000000-0000-0000-0000-000000000201",
                            episodes = listOf(episode),
                        ),
                    playbackResult = JellyfinPlaybackResolutionResult.Success(testPlaybackSource()),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            viewModel.openDetails(series)
            advanceUntilIdle()

            viewModel.resolveItem(episode)
            advanceUntilIdle()

            assertEquals(listOf(PlaybackItemResolveCall(SERVER_URL, testSession(), episode)), repository.resolveItemCalls)
            assertEquals(PlaybackResolutionStatus.Ready, viewModel.uiState.value.playbackStatus)
        }

    @Test
    fun `home loads supported library rows and filters them locally`() =
        runTest(mainDispatcherRule.dispatcher) {
            val movies = testLibrary()
            val shows = JellyfinLibrary("00000000-0000-0000-0000-000000000011", "Séries", "TVSHOWS")
            val music = JellyfinLibrary("00000000-0000-0000-0000-000000000012", "Musique", "MUSIC")
            val movieRow = JellyfinHomeRow(movies, listOf(testCatalogItem()))
            val showRow =
                JellyfinHomeRow(
                    shows,
                    listOf(
                        JellyfinCatalogItem(
                            id = "00000000-0000-0000-0000-000000000101",
                            name = "Example Series",
                            type = JellyfinCatalogItemType.Series,
                        ),
                    ),
                )
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(movies, shows, music)),
                    homeResult = JellyfinHomeResult.Success(listOf(movieRow, showRow)),
                )
            val viewModel = createViewModel(repository = repository)

            advanceUntilIdle()

            assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            assertEquals(listOf(movieRow, showRow), viewModel.uiState.value.homeRows)
            assertEquals(
                listOf(HomeFetchCall(SERVER_URL, testSession(), listOf(movies, shows))),
                repository.homeCalls,
            )

            viewModel.selectLibrary(shows.id)

            assertEquals(shows.id, viewModel.uiState.value.selectedLibraryId)
            assertEquals(listOf(showRow), viewModel.uiState.value.visibleHomeRows)
            assertEquals(1, repository.homeCalls.size)
        }

    @Test
    fun `catalog movie activation resolves the selected item and keeps home rows`() =
        runTest(mainDispatcherRule.dispatcher) {
            val item = testCatalogItem()
            val row = JellyfinHomeRow(testLibrary(), listOf(item))
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    homeResult = JellyfinHomeResult.Success(listOf(row)),
                    playbackResult = JellyfinPlaybackResolutionResult.Success(testPlaybackSource()),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.resolveItem(item)
            advanceUntilIdle()

            assertEquals(
                listOf(PlaybackItemResolveCall(SERVER_URL, testSession(), item)),
                repository.resolveItemCalls,
            )
            assertEquals(item.id, viewModel.uiState.value.activeItemId)
            assertEquals(listOf(row), viewModel.uiState.value.homeRows)
            assertEquals(PlaybackResolutionStatus.Ready, viewModel.uiState.value.playbackStatus)
        }

    @Test
    fun `series activation does not start playback before details scope`() =
        runTest(mainDispatcherRule.dispatcher) {
            val series = testCatalogItem().copy(type = JellyfinCatalogItemType.Series)
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    homeResult = JellyfinHomeResult.Success(listOf(JellyfinHomeRow(testLibrary(), listOf(series)))),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.resolveItem(series)
            advanceUntilIdle()

            assertEquals(emptyList<PlaybackItemResolveCall>(), repository.resolveItemCalls)
            assertEquals(PlaybackResolutionStatus.Idle, viewModel.uiState.value.playbackStatus)
        }

    @Test
    fun `empty home rows expose retryable empty state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    homeResult = JellyfinHomeResult.Success(emptyList()),
                )
            val viewModel = createViewModel(repository = repository)

            advanceUntilIdle()

            assertEquals(LibrariesStatus.Empty, viewModel.uiState.value.status)
            assertEquals(listOf(testLibrary()), viewModel.uiState.value.libraries)
        }

    @Test
    fun `home failures expose retryable catalog errors without clearing session`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cases =
                listOf(
                    JellyfinHomeResult.Unreachable to LibrariesUiError.Unreachable,
                    JellyfinHomeResult.SecureConnectionFailed to LibrariesUiError.SecureConnection,
                    JellyfinHomeResult.ServerRejected to LibrariesUiError.ServerRejected,
                    JellyfinHomeResult.InvalidResponse to LibrariesUiError.InvalidResponse,
                    JellyfinHomeResult.UnexpectedFailure to LibrariesUiError.Unexpected,
                )

            cases.forEach { (homeResult, expectedError) ->
                val store = FakeSessionStore(restored(testSession()))
                val viewModel =
                    createViewModel(
                        repository =
                            FakeLibrariesRepository(
                                result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                                homeResult = homeResult,
                            ),
                        store = store,
                    )

                advanceUntilIdle()

                assertEquals(LibrariesStatus.Error, viewModel.uiState.value.status)
                assertEquals(expectedError, viewModel.uiState.value.error)
                assertEquals(0, store.clearCalls)
            }
        }

    @Test
    fun `valid saved session is restored and libraries load automatically`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = testSession()
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                )
            val viewModel = createViewModel(repository = repository, session = restored(session))

            advanceUntilIdle()

            assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            assertEquals(listOf(testLibrary()), viewModel.uiState.value.libraries)
            assertEquals(listOf(LibraryFetchCall(SERVER_URL, session)), repository.fetchCalls)
        }

    @Test
    fun `missing server opens server configuration without reading session`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore(restored(testSession()))
            val viewModel = createViewModel(serverUrl = null, store = store)

            advanceUntilIdle()

            assertEquals(LibrariesStatus.NeedsServer, viewModel.uiState.value.status)
            assertEquals(0, store.restoreCalls)
        }

    @Test
    fun `missing session opens login for saved server`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeLibrariesRepository()
            val viewModel =
                createViewModel(
                    repository = repository,
                    session = JellyfinSessionRestoreResult.Missing,
                )

            advanceUntilIdle()

            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            assertEquals(SERVER_URL, viewModel.uiState.value.serverUrl)
            assertNull(viewModel.uiState.value.sessionIssue)
            assertEquals(0, repository.fetchCalls.size)
        }

    @Test
    fun `unreadable encrypted session opens login with explanation`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel(session = JellyfinSessionRestoreResult.Unreadable)

            advanceUntilIdle()

            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            assertEquals(LibrariesSessionIssue.Unreadable, viewModel.uiState.value.sessionIssue)
        }

    @Test
    fun `revoked session is cleared before login is requested`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore(restored(testSession()))
            val viewModel =
                createViewModel(
                    repository = FakeLibrariesRepository(JellyfinLibrariesResult.InvalidSession),
                    store = store,
                )

            advanceUntilIdle()

            assertEquals(1, store.clearCalls)
            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            assertEquals(LibrariesSessionIssue.Expired, viewModel.uiState.value.sessionIssue)
        }

    @Test
    fun `empty and retryable failure states are exposed without clearing session`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cases =
                listOf(
                    JellyfinLibrariesResult.Success(emptyList()) to
                        (LibrariesStatus.Empty to null),
                    JellyfinLibrariesResult.Unreachable to
                        (LibrariesStatus.Error to LibrariesUiError.Unreachable),
                    JellyfinLibrariesResult.SecureConnectionFailed to
                        (LibrariesStatus.Error to LibrariesUiError.SecureConnection),
                    JellyfinLibrariesResult.ServerRejected to
                        (LibrariesStatus.Error to LibrariesUiError.ServerRejected),
                    JellyfinLibrariesResult.InvalidResponse to
                        (LibrariesStatus.Error to LibrariesUiError.InvalidResponse),
                    JellyfinLibrariesResult.UnexpectedFailure to
                        (LibrariesStatus.Error to LibrariesUiError.Unexpected),
                )

            cases.forEach { (result, expected) ->
                val store = FakeSessionStore(restored(testSession()))
                val viewModel = createViewModel(repository = FakeLibrariesRepository(result), store = store)

                advanceUntilIdle()

                assertEquals(expected.first, viewModel.uiState.value.status)
                assertEquals(expected.second, viewModel.uiState.value.error)
                assertEquals(0, store.clearCalls)
            }
        }

    @Test
    fun `successful login reloads newly saved session without credentials`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore(JellyfinSessionRestoreResult.Missing)
            val repository =
                FakeLibrariesRepository(
                    JellyfinLibrariesResult.Success(listOf(testLibrary())),
                )
            val viewModel = createViewModel(repository = repository, store = store)
            advanceUntilIdle()
            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            store.restoreResult = restored(testSession())

            viewModel.onLoginSucceeded()
            advanceUntilIdle()

            assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            assertEquals(1, repository.fetchCalls.size)
        }

    @Test
    fun `rejected freshly saved session requests a fresh login state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore(JellyfinSessionRestoreResult.Missing)
            val viewModel =
                createViewModel(
                    repository = FakeLibrariesRepository(JellyfinLibrariesResult.InvalidSession),
                    store = store,
                )
            advanceUntilIdle()
            val firstLoginRequestId = viewModel.uiState.value.loginRequestId
            store.restoreResult = restored(testSession())

            viewModel.onLoginSucceeded()
            advanceUntilIdle()

            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            assertNotEquals(firstLoginRequestId, viewModel.uiState.value.loginRequestId)
        }

    @Test
    fun `retry reloads libraries after a transient failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeLibrariesRepository(JellyfinLibrariesResult.Unreachable)
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            repository.result = JellyfinLibrariesResult.Success(listOf(testLibrary()))

            viewModel.retry()
            advanceUntilIdle()

            assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            assertEquals(2, repository.fetchCalls.size)
        }

    @Test
    fun `library activation exposes resolved playback source without discarding libraries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val source = testPlaybackSource()
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackResult = JellyfinPlaybackResolutionResult.Success(source),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()

            assertEquals(PlaybackResolutionStatus.Ready, viewModel.uiState.value.playbackStatus)
            assertEquals(source, viewModel.uiState.value.playbackSource)
            assertEquals(listOf(testLibrary()), viewModel.uiState.value.libraries)
            assertEquals(
                listOf(PlaybackResolveCall(SERVER_URL, testSession(), testLibrary().id)),
                repository.resolveCalls,
            )
        }

    @Test
    fun `empty and retryable playback outcomes remain on libraries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cases =
                listOf(
                    JellyfinPlaybackResolutionResult.Empty to
                        (PlaybackResolutionStatus.Empty to null),
                    JellyfinPlaybackResolutionResult.Unreachable to
                        (PlaybackResolutionStatus.Error to LibrariesUiError.Unreachable),
                    JellyfinPlaybackResolutionResult.SecureConnectionFailed to
                        (PlaybackResolutionStatus.Error to LibrariesUiError.SecureConnection),
                    JellyfinPlaybackResolutionResult.ServerRejected to
                        (PlaybackResolutionStatus.Error to LibrariesUiError.ServerRejected),
                    JellyfinPlaybackResolutionResult.InvalidResponse to
                        (PlaybackResolutionStatus.Error to LibrariesUiError.InvalidResponse),
                    JellyfinPlaybackResolutionResult.UnexpectedFailure to
                        (PlaybackResolutionStatus.Error to LibrariesUiError.Unexpected),
                )

            cases.forEach { (result, expected) ->
                val repository =
                    FakeLibrariesRepository(
                        result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                        playbackResult = result,
                    )
                val viewModel = createViewModel(repository = repository)
                advanceUntilIdle()

                viewModel.resolveLibrary(testLibrary().id)
                advanceUntilIdle()

                assertEquals(expected.first, viewModel.uiState.value.playbackStatus)
                assertEquals(expected.second, viewModel.uiState.value.playbackError)
                assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            }
        }

    @Test
    fun `revoked session during playback resolution is cleared and requests login`() =
        runTest(mainDispatcherRule.dispatcher) {
            val store = FakeSessionStore(restored(testSession()))
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackResult = JellyfinPlaybackResolutionResult.InvalidSession,
                )
            val viewModel = createViewModel(repository = repository, store = store)
            advanceUntilIdle()

            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()

            assertEquals(1, store.clearCalls)
            assertEquals(LibrariesStatus.NeedsLogin, viewModel.uiState.value.status)
            assertEquals(LibrariesSessionIssue.Expired, viewModel.uiState.value.sessionIssue)
        }

    @Test
    fun `duplicate activation is suppressed while playback resolution is loading`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackResult = JellyfinPlaybackResolutionResult.Empty,
                    beforePlaybackResult = { gate.await() },
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.resolveLibrary(testLibrary().id)
            runCurrent()
            viewModel.resolveLibrary(testLibrary().id)
            runCurrent()

            assertEquals(PlaybackResolutionStatus.Loading, viewModel.uiState.value.playbackStatus)
            assertEquals(1, repository.resolveCalls.size)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `library activation retries after a playback failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackResult = JellyfinPlaybackResolutionResult.Unreachable,
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()
            repository.playbackResult = JellyfinPlaybackResolutionResult.Success(testPlaybackSource())

            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()

            assertEquals(PlaybackResolutionStatus.Ready, viewModel.uiState.value.playbackStatus)
            assertEquals(2, repository.resolveCalls.size)
        }

    @Test
    fun `closing player clears only playback state and keeps loaded libraries`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackResult = JellyfinPlaybackResolutionResult.Success(testPlaybackSource()),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()

            viewModel.closePlayer()

            assertEquals(LibrariesStatus.Success, viewModel.uiState.value.status)
            assertEquals(listOf(testLibrary()), viewModel.uiState.value.libraries)
            assertEquals(PlaybackResolutionStatus.Idle, viewModel.uiState.value.playbackStatus)
            assertNull(viewModel.uiState.value.playbackSource)
            assertNull(viewModel.uiState.value.activeLibraryId)
        }

    @Test
    fun `playback report uses the restored session without exposing it in UI state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()
            val report = testPlaybackReport()

            viewModel.reportPlayback(report)

            assertEquals(
                listOf(PlaybackReportCall(SERVER_URL, testSession(), report)),
                repository.reportCalls,
            )
            assertEquals(SERVER_URL, viewModel.uiState.value.serverUrl)
            assertEquals(false, viewModel.uiState.value.toString().contains("test-access-token"))
        }

    @Test
    fun `unexpected playback exception becomes retryable UI failure`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLibrariesRepository(
                    result = JellyfinLibrariesResult.Success(listOf(testLibrary())),
                    playbackFailure = IllegalStateException("fixture failure"),
                )
            val viewModel = createViewModel(repository = repository)
            advanceUntilIdle()

            viewModel.resolveLibrary(testLibrary().id)
            advanceUntilIdle()

            assertEquals(PlaybackResolutionStatus.Error, viewModel.uiState.value.playbackStatus)
            assertEquals(LibrariesUiError.Unexpected, viewModel.uiState.value.playbackError)
        }

    private fun createViewModel(
        serverUrl: String? = SERVER_URL,
        repository: FakeLibrariesRepository = FakeLibrariesRepository(),
        session: JellyfinSessionRestoreResult = restored(testSession()),
        store: FakeSessionStore = FakeSessionStore(session),
    ) = JellyfinLibrariesViewModel(
        jellyfinRepository = repository,
        settingsRepository = FakeSettingsRepository(serverUrl),
        sessionStore = store,
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    private companion object {
        const val SERVER_URL = "https://media.example.test/"

        fun testSession() =
            JellyfinSession(
                accessToken = "test-access-token",
                userId = "00000000-0000-0000-0000-000000000001",
                serverId = "test-server-id",
            )

        fun testLibrary() =
            JellyfinLibrary(
                id = "00000000-0000-0000-0000-000000000010",
                name = "Films",
                collectionType = "MOVIES",
            )

        fun testCatalogItem() =
            JellyfinCatalogItem(
                id = "00000000-0000-0000-0000-000000000100",
                name = "Example Movie",
                type = JellyfinCatalogItemType.Movie,
                productionYear = 2026,
            )

        fun testEpisode(
            id: String = "00000000-0000-0000-0000-000000000301",
            season: Int = 1,
        ) = JellyfinCatalogItem(
            id = id,
            name = "Pilot",
            type = JellyfinCatalogItemType.Episode,
            playbackPositionTicks = 45_000_000,
            indexNumber = 1,
            parentIndexNumber = season,
        )

        fun restored(session: JellyfinSession) = JellyfinSessionRestoreResult.Restored(session)

        fun testPlaybackSource() =
            JellyfinPlaybackSource(
                item =
                    JellyfinPlayableItem(
                        id = "00000000-0000-0000-0000-000000000100",
                        name = "Example Movie",
                        type = "MOVIE",
                    ),
                mediaSourceId = "media-source",
                playSessionId = "play-session",
                method = PlaybackMethod.DIRECT_PLAY,
                container = "mp4",
                url = "https://media.example.test/video.mp4",
                requiredHttpHeaders = emptyMap(),
            )

        fun testPlaybackReport() =
            JellyfinPlaybackReport(
                kind = JellyfinPlaybackReportKind.Progress,
                itemId = "00000000-0000-0000-0000-000000000100",
                mediaSourceId = "media-source",
                playSessionId = "play-session",
                method = PlaybackMethod.DIRECT_PLAY,
                positionMs = 12_345,
                isPaused = false,
                isSeekable = true,
            )
    }
}

private data class LibraryFetchCall(
    val serverUrl: String,
    val session: JellyfinSession,
)

private data class HomeFetchCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val libraries: List<JellyfinLibrary>,
)

private data class PlaybackResolveCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val libraryId: String,
)

private data class PlaybackItemResolveCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val item: JellyfinCatalogItem,
)

private data class PlaybackReportCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val report: JellyfinPlaybackReport,
)

private data class SeriesDetailsCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val series: JellyfinCatalogItem,
)

private data class SeasonEpisodesCall(
    val serverUrl: String,
    val session: JellyfinSession,
    val seriesId: String,
    val seasonId: String,
)

private class FakeLibrariesRepository(
    var result: JellyfinLibrariesResult = JellyfinLibrariesResult.UnexpectedFailure,
    var homeResult: JellyfinHomeResult? = null,
    var playbackResult: JellyfinPlaybackResolutionResult =
        JellyfinPlaybackResolutionResult.UnexpectedFailure,
    var playbackFailure: Throwable? = null,
    var seriesResult: JellyfinSeriesDetailsResult = JellyfinSeriesDetailsResult.UnexpectedFailure,
    var episodesResult: JellyfinEpisodesResult = JellyfinEpisodesResult.UnexpectedFailure,
    var beforeSeriesResult: suspend () -> Unit = {},
    var beforePlaybackResult: suspend () -> Unit = {},
) : JellyfinRepository {
    val fetchCalls = mutableListOf<LibraryFetchCall>()
    val homeCalls = mutableListOf<HomeFetchCall>()
    val resolveCalls = mutableListOf<PlaybackResolveCall>()
    val resolveItemCalls = mutableListOf<PlaybackItemResolveCall>()
    val reportCalls = mutableListOf<PlaybackReportCall>()
    val seriesCalls = mutableListOf<SeriesDetailsCall>()
    val episodeCalls = mutableListOf<SeasonEpisodesCall>()

    override suspend fun connect(serverUrl: String): JellyfinConnectionResult =
        JellyfinConnectionResult.UnexpectedFailure

    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthenticationResult = JellyfinAuthenticationResult.UnexpectedFailure

    override suspend fun fetchLibraries(
        serverUrl: String,
        session: JellyfinSession,
    ): JellyfinLibrariesResult {
        fetchCalls += LibraryFetchCall(serverUrl, session)
        return result
    }

    override suspend fun fetchHomeRows(
        serverUrl: String,
        session: JellyfinSession,
        libraries: List<JellyfinLibrary>,
    ): JellyfinHomeResult {
        homeCalls += HomeFetchCall(serverUrl, session, libraries)
        return homeResult
            ?: JellyfinHomeResult.Success(
                libraries.map { library ->
                    JellyfinHomeRow(
                        library = library,
                        items =
                            listOf(
                                JellyfinCatalogItem(
                                    id = "00000000-0000-0000-0000-000000000100",
                                    name = "Example Movie",
                                    type = JellyfinCatalogItemType.Movie,
                                ),
                            ),
                    )
                },
            )
    }

    override suspend fun resolvePlaybackSource(
        serverUrl: String,
        session: JellyfinSession,
        libraryId: String,
    ): JellyfinPlaybackResolutionResult {
        resolveCalls += PlaybackResolveCall(serverUrl, session, libraryId)
        beforePlaybackResult()
        playbackFailure?.let { throw it }
        return playbackResult
    }

    override suspend fun fetchSeriesDetails(
        serverUrl: String,
        session: JellyfinSession,
        series: JellyfinCatalogItem,
    ): JellyfinSeriesDetailsResult {
        seriesCalls += SeriesDetailsCall(serverUrl, session, series)
        beforeSeriesResult()
        return seriesResult
    }

    override suspend fun fetchSeasonEpisodes(
        serverUrl: String,
        session: JellyfinSession,
        seriesId: String,
        seasonId: String,
    ): JellyfinEpisodesResult {
        episodeCalls += SeasonEpisodesCall(serverUrl, session, seriesId, seasonId)
        return episodesResult
    }

    override suspend fun resolvePlaybackItem(
        serverUrl: String,
        session: JellyfinSession,
        item: JellyfinCatalogItem,
    ): JellyfinPlaybackResolutionResult {
        resolveItemCalls += PlaybackItemResolveCall(serverUrl, session, item)
        beforePlaybackResult()
        playbackFailure?.let { throw it }
        return playbackResult
    }

    override suspend fun reportPlayback(
        serverUrl: String,
        session: JellyfinSession,
        report: JellyfinPlaybackReport,
    ): JellyfinPlaybackReportResult {
        reportCalls += PlaybackReportCall(serverUrl, session, report)
        return JellyfinPlaybackReportResult.Success
    }
}

private class FakeSettingsRepository(initialUrl: String?) : ServerSettingsRepository {
    override val serverUrl: Flow<String?> = MutableStateFlow(initialUrl)

    override suspend fun saveServerUrl(serverUrl: String) = Unit
}

private class FakeSessionStore(
    var restoreResult: JellyfinSessionRestoreResult,
) : JellyfinSessionStore {
    var restoreCalls = 0
        private set
    var clearCalls = 0
        private set

    override suspend fun save(session: JellyfinSession) {
        restoreResult = JellyfinSessionRestoreResult.Restored(session)
    }

    override suspend fun restore(): JellyfinSessionRestoreResult {
        restoreCalls += 1
        return restoreResult
    }

    override suspend fun clear() {
        clearCalls += 1
        restoreResult = JellyfinSessionRestoreResult.Missing
    }
}
