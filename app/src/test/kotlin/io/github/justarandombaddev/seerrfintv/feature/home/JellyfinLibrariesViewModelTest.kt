package io.github.justarandombaddev.seerrfintv.feature.home

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
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

        fun restored(session: JellyfinSession) = JellyfinSessionRestoreResult.Restored(session)
    }
}

private data class LibraryFetchCall(
    val serverUrl: String,
    val session: JellyfinSession,
)

private class FakeLibrariesRepository(
    var result: JellyfinLibrariesResult = JellyfinLibrariesResult.UnexpectedFailure,
) : JellyfinRepository {
    val fetchCalls = mutableListOf<LibraryFetchCall>()

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
