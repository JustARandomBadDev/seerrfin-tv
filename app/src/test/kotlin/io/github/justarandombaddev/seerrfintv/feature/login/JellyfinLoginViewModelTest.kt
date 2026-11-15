package io.github.justarandombaddev.seerrfintv.feature.login

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinLoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `blank username reports validation error without authenticating`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeLoginJellyfinRepository()
            val viewModel = createViewModel(repository = repository)

            viewModel.onUsernameChanged("   ")
            viewModel.onPasswordChanged("password")
            viewModel.login()

            assertEquals(0, repository.calls)
            assertEquals(JellyfinLoginStatus.Error, viewModel.uiState.value.status)
            assertEquals(JellyfinLoginUiError.EmptyUsername, viewModel.uiState.value.error)
        }

    @Test
    fun `login UI state string representation redacts password`() {
        val state = JellyfinLoginUiState(password = "private-password")

        val rendered = state.toString()

        assertTrue(rendered.contains("<redacted>"))
        assertTrue(!rendered.contains("private-password"))
    }

    @Test
    fun `blank password is passed unchanged to Jellyfin`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeLoginJellyfinRepository()
            val viewModel = createViewModel(repository = repository)
            viewModel.onUsernameChanged("  alice  ")

            viewModel.login()
            advanceUntilIdle()

            assertEquals(listOf(LoginCall("https://media.example.test/", "alice", "")), repository.loginCalls)
        }

    @Test
    fun `duplicate login is ignored while authentication is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeLoginJellyfinRepository(gate = gate)
            val viewModel = createViewModel(repository = repository)
            viewModel.onUsernameChanged("alice")

            viewModel.login()
            viewModel.login()
            runCurrent()

            assertEquals(1, repository.calls)
            assertEquals(JellyfinLoginStatus.Loading, viewModel.uiState.value.status)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `successful login persists session and clears password`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = testSession()
            val repository =
                FakeLoginJellyfinRepository(
                    result = JellyfinAuthenticationResult.Authenticated(session, "Alice"),
                )
            val store = FakeJellyfinSessionStore()
            val viewModel = createViewModel(repository = repository, store = store)
            viewModel.onUsernameChanged("alice")
            viewModel.onPasswordChanged("private-password")

            viewModel.login()
            advanceUntilIdle()

            assertEquals(listOf(session), store.savedSessions)
            assertEquals(JellyfinLoginStatus.Authenticated, viewModel.uiState.value.status)
            assertEquals("Alice", viewModel.uiState.value.displayName)
            assertEquals("", viewModel.uiState.value.password)
        }

    @Test
    fun `storage failure does not report authenticated and clears password`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeLoginJellyfinRepository(
                    result = JellyfinAuthenticationResult.Authenticated(testSession(), "Alice"),
                )
            val store = FakeJellyfinSessionStore(saveFailure = IOException("disk unavailable"))
            val viewModel = createViewModel(repository = repository, store = store)
            viewModel.onUsernameChanged("alice")
            viewModel.onPasswordChanged("private-password")

            viewModel.login()
            advanceUntilIdle()

            assertEquals(JellyfinLoginStatus.Error, viewModel.uiState.value.status)
            assertEquals(JellyfinLoginUiError.SessionStorage, viewModel.uiState.value.error)
            assertEquals("", viewModel.uiState.value.password)
        }

    @Test
    fun `repository failures map to actionable errors`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cases =
                listOf(
                    JellyfinAuthenticationResult.InvalidCredentials to JellyfinLoginUiError.InvalidCredentials,
                    JellyfinAuthenticationResult.Unreachable to JellyfinLoginUiError.Unreachable,
                    JellyfinAuthenticationResult.SecureConnectionFailed to JellyfinLoginUiError.SecureConnection,
                    JellyfinAuthenticationResult.ServerRejected to JellyfinLoginUiError.ServerRejected,
                    JellyfinAuthenticationResult.InvalidResponse to JellyfinLoginUiError.InvalidResponse,
                    JellyfinAuthenticationResult.UnexpectedFailure to JellyfinLoginUiError.Unexpected,
                )

            cases.forEach { (result, expectedError) ->
                val viewModel = createViewModel(repository = FakeLoginJellyfinRepository(result = result))
                viewModel.onUsernameChanged("alice")
                viewModel.onPasswordChanged("password")

                viewModel.login()
                advanceUntilIdle()

                assertEquals(JellyfinLoginStatus.Error, viewModel.uiState.value.status)
                assertEquals(expectedError, viewModel.uiState.value.error)
                assertTrue(viewModel.uiState.value.password.isNotEmpty())
            }
        }

    private fun createViewModel(
        repository: JellyfinRepository = FakeLoginJellyfinRepository(),
        store: JellyfinSessionStore = FakeJellyfinSessionStore(),
    ) = JellyfinLoginViewModel(
        serverUrl = "https://media.example.test/",
        jellyfinRepository = repository,
        sessionStore = store,
        ioDispatcher = mainDispatcherRule.dispatcher,
    )

    private fun testSession() =
        JellyfinSession(
            accessToken = "test-access-token",
            userId = "test-user-id",
            serverId = "test-server-id",
        )
}

private data class LoginCall(
    val serverUrl: String,
    val username: String,
    val password: String,
)

private class FakeLoginJellyfinRepository(
    private val result: JellyfinAuthenticationResult = JellyfinAuthenticationResult.InvalidCredentials,
    private val gate: CompletableDeferred<Unit>? = null,
) : JellyfinRepository {
    val loginCalls = mutableListOf<LoginCall>()
    val calls: Int get() = loginCalls.size

    override suspend fun connect(serverUrl: String): JellyfinConnectionResult =
        JellyfinConnectionResult.UnexpectedFailure

    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthenticationResult {
        loginCalls += LoginCall(serverUrl, username, password)
        gate?.await()
        return result
    }

    override suspend fun fetchLibraries(
        serverUrl: String,
        session: JellyfinSession,
    ): JellyfinLibrariesResult = JellyfinLibrariesResult.UnexpectedFailure

    override suspend fun resolvePlaybackSource(
        serverUrl: String,
        session: JellyfinSession,
        libraryId: String,
    ): JellyfinPlaybackResolutionResult = JellyfinPlaybackResolutionResult.UnexpectedFailure
}

private class FakeJellyfinSessionStore(
    private val saveFailure: Exception? = null,
) : JellyfinSessionStore {
    val savedSessions = mutableListOf<JellyfinSession>()

    override suspend fun save(session: JellyfinSession) {
        saveFailure?.let { throw it }
        savedSessions += session
    }

    override suspend fun restore(): JellyfinSessionRestoreResult =
        savedSessions.lastOrNull()?.let(JellyfinSessionRestoreResult::Restored)
            ?: JellyfinSessionRestoreResult.Missing

    override suspend fun clear() {
        savedSessions.clear()
    }
}
