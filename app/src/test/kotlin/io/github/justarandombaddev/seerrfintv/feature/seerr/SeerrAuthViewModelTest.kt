package io.github.justarandombaddev.seerrfintv.feature.seerr

import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrPermissions
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrUser
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectChallenge
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectCheckResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectInitiationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSessionValidationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionStore
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeerrAuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `restored session is verified with auth me before showing user permissions`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = testSession()
            val user = testUser(32L)
            val repository = FakeSeerrRepository(validation = SeerrSessionValidationResult.Authenticated(user))
            val store = FakeSeerrSessionStore(restored = SeerrSessionRestoreResult.Restored(session))

            val viewModel = createViewModel(repository, store)
            advanceUntilIdle()

            assertEquals(listOf(session), repository.validatedSessions)
            assertEquals(SeerrAuthStatus.Authenticated, viewModel.uiState.value.status)
            assertEquals(user, viewModel.uiState.value.user)
            assertTrue(viewModel.uiState.value.user!!.permissions.canRequestMovies)
            assertTrue(viewModel.uiState.value.user!!.permissions.canRequestSeries)
        }

    @Test
    fun `expired restored session is cleared and asks for a new connection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = testSession()
            val repository = FakeSeerrRepository(validation = SeerrSessionValidationResult.Expired)
            val store = FakeSeerrSessionStore(restored = SeerrSessionRestoreResult.Restored(session))

            val viewModel = createViewModel(repository, store)
            advanceUntilIdle()

            assertEquals(1, store.clearCalls)
            assertEquals(SeerrAuthStatus.Editing, viewModel.uiState.value.status)
            assertEquals(SeerrAuthError.SessionExpired, viewModel.uiState.value.error)
            assertEquals(session.serverUrl, viewModel.uiState.value.serverUrl)
        }

    @Test
    fun `invalid address is rejected before contacting Seerr`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = FakeSeerrRepository()
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.onServerUrlChanged("https://user:secret@requests.example.test")
            viewModel.connect()
            advanceUntilIdle()

            assertEquals(0, repository.initiateCalls)
            assertEquals(SeerrAuthStatus.Error, viewModel.uiState.value.status)
            assertEquals(SeerrAuthError.InvalidServerAddress, viewModel.uiState.value.error)
        }

    @Test
    fun `quick connect polls authorization then stores verified user session`() =
        runTest(mainDispatcherRule.dispatcher) {
            val session = testSession()
            val user = testUser(262_144L)
            val repository =
                FakeSeerrRepository(
                    initiation =
                        SeerrQuickConnectInitiationResult.Ready(
                            SeerrQuickConnectChallenge("246810", "private-secret"),
                        ),
                    checks =
                        ArrayDeque(
                            listOf(
                                SeerrQuickConnectCheckResult.Pending,
                                SeerrQuickConnectCheckResult.Authorized,
                            ),
                        ),
                    authentication = SeerrAuthenticationResult.Authenticated(session, user),
                )
            val store = FakeSeerrSessionStore()
            val settings = FakeSeerrSettingsRepository()
            val viewModel = createViewModel(repository, store, settings)
            advanceUntilIdle()

            viewModel.onServerUrlChanged(" requests.example.test ")
            viewModel.connect()
            runCurrent()

            assertEquals(SeerrAuthStatus.WaitingForAuthorization, viewModel.uiState.value.status)
            assertEquals("246810", viewModel.uiState.value.code)
            assertFalse(viewModel.uiState.value.toString().contains("private-secret"))

            advanceTimeBy(3L)
            advanceUntilIdle()

            assertEquals(listOf("http://requests.example.test/"), settings.savedUrls)
            assertEquals(listOf(session), store.savedSessions)
            assertEquals(SeerrAuthStatus.Authenticated, viewModel.uiState.value.status)
            assertEquals(user, viewModel.uiState.value.user)
            assertTrue(user.permissions.canRequestMovies)
            assertFalse(user.permissions.canRequestSeries)
        }

    @Test
    fun `session storage failure never reports authenticated`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeSeerrRepository(
                    initiation =
                        SeerrQuickConnectInitiationResult.Ready(
                            SeerrQuickConnectChallenge("246810", "private-secret"),
                        ),
                    checks = ArrayDeque(listOf(SeerrQuickConnectCheckResult.Authorized)),
                    authentication =
                        SeerrAuthenticationResult.Authenticated(testSession(), testUser(32L)),
                )
            val store = FakeSeerrSessionStore(saveFailure = IOException("disk unavailable"))
            val viewModel = createViewModel(repository, store)
            advanceUntilIdle()
            viewModel.onServerUrlChanged("https://requests.example.test")

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(SeerrAuthStatus.Error, viewModel.uiState.value.status)
            assertEquals(SeerrAuthError.SessionStorage, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.user)
        }

    @Test
    fun `quick connect unavailable is actionable and does not expose a code`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository =
                FakeSeerrRepository(initiation = SeerrQuickConnectInitiationResult.Unavailable)
            val viewModel = createViewModel(repository)
            advanceUntilIdle()
            viewModel.onServerUrlChanged("https://requests.example.test")

            viewModel.connect()
            advanceUntilIdle()

            assertEquals(SeerrAuthStatus.Error, viewModel.uiState.value.status)
            assertEquals(SeerrAuthError.QuickConnectUnavailable, viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.code)
        }

    private fun createViewModel(
        repository: SeerrRepository = FakeSeerrRepository(),
        store: SeerrSessionStore = FakeSeerrSessionStore(),
        settings: SeerrSettingsRepository = FakeSeerrSettingsRepository(),
    ) = SeerrAuthViewModel(
        repository = repository,
        sessionStore = store,
        settingsRepository = settings,
        urlNormalizer = ServerUrlNormalizer(),
        ioDispatcher = mainDispatcherRule.dispatcher,
        pollIntervalMillis = 1L,
        maxPollAttempts = 3,
    )

    private fun testSession() =
        SeerrSession(
            serverUrl = "https://requests.example.test/",
            cookieName = "connect.sid",
            cookieValue = "s%3Atest-cookie.signature",
            expiresAtEpochMillis = 4_102_444_800_000L,
            secure = true,
        )

    private fun testUser(permissions: Long) =
        SeerrUser(
            id = 7L,
            displayName = "Alice",
            permissions = SeerrPermissions(permissions),
        )
}

private class FakeSeerrRepository(
    private val initiation: SeerrQuickConnectInitiationResult =
        SeerrQuickConnectInitiationResult.ServerRejected,
    private val checks: ArrayDeque<SeerrQuickConnectCheckResult> = ArrayDeque(),
    private val authentication: SeerrAuthenticationResult = SeerrAuthenticationResult.AccessDenied,
    private val validation: SeerrSessionValidationResult = SeerrSessionValidationResult.Expired,
) : SeerrRepository {
    var initiateCalls = 0
        private set
    val validatedSessions = mutableListOf<SeerrSession>()

    override suspend fun initiateQuickConnect(serverUrl: String): SeerrQuickConnectInitiationResult {
        initiateCalls += 1
        return initiation
    }

    override suspend fun checkQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrQuickConnectCheckResult = checks.removeFirst()

    override suspend fun authenticateQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrAuthenticationResult = authentication

    override suspend fun validateSession(session: SeerrSession): SeerrSessionValidationResult {
        validatedSessions += session
        return validation
    }
}

private class FakeSeerrSessionStore(
    private val restored: SeerrSessionRestoreResult = SeerrSessionRestoreResult.Missing,
    private val saveFailure: Exception? = null,
) : SeerrSessionStore {
    val savedSessions = mutableListOf<SeerrSession>()
    var clearCalls = 0
        private set

    override suspend fun save(session: SeerrSession) {
        saveFailure?.let { throw it }
        savedSessions += session
    }

    override suspend fun restore(): SeerrSessionRestoreResult = restored

    override suspend fun clear() {
        clearCalls += 1
    }
}

private class FakeSeerrSettingsRepository(
    initialUrl: String? = null,
) : SeerrSettingsRepository {
    private val mutableUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = mutableUrl
    val savedUrls = mutableListOf<String>()

    override suspend fun saveServerUrl(serverUrl: String) {
        savedUrls += serverUrl
        mutableUrl.value = serverUrl
    }
}
