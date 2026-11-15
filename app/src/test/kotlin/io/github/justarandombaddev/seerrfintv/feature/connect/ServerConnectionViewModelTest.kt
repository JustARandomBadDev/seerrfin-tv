package io.github.justarandombaddev.seerrfintv.feature.connect

import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `saved server URL populates initial input`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings = FakeServerSettingsRepository("https://media.example.test/")
            val viewModel = createViewModel(settings = settings)

            runCurrent()

            assertEquals("https://media.example.test/", viewModel.uiState.value.serverUrl)
            assertEquals(ServerConnectionStatus.Idle, viewModel.uiState.value.status)
        }

    @Test
    fun `invalid address reports error without contacting repository`() =
        runTest(mainDispatcherRule.dispatcher) {
            val jellyfin = FakeJellyfinRepository()
            val viewModel = createViewModel(jellyfin = jellyfin)
            runCurrent()

            viewModel.onServerUrlChanged("ftp://media.example.test")
            viewModel.connect()

            assertEquals(0, jellyfin.calls)
            assertEquals(ServerConnectionStatus.Error, viewModel.uiState.value.status)
            assertEquals(ServerConnectionUiError.UnsupportedScheme, viewModel.uiState.value.error)
        }

    @Test
    fun `successful connection persists normalized address`() =
        runTest(mainDispatcherRule.dispatcher) {
            val settings = FakeServerSettingsRepository()
            val jellyfin =
                FakeJellyfinRepository(
                    result = JellyfinConnectionResult.Connected("http://media.example.test/"),
                )
            val viewModel = createViewModel(jellyfin = jellyfin, settings = settings)
            runCurrent()

            viewModel.onServerUrlChanged("media.example.test")
            viewModel.connect()

            assertEquals(ServerConnectionStatus.Loading, viewModel.uiState.value.status)
            advanceUntilIdle()
            assertEquals(ServerConnectionStatus.Connected, viewModel.uiState.value.status)
            assertEquals("http://media.example.test/", viewModel.uiState.value.serverUrl)
            assertEquals(listOf("http://media.example.test/"), settings.savedUrls)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `returning from login restores editable server state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val jellyfin =
                FakeJellyfinRepository(
                    result = JellyfinConnectionResult.Connected("https://media.example.test/"),
                )
            val viewModel = createViewModel(jellyfin = jellyfin)
            runCurrent()
            viewModel.onServerUrlChanged("https://media.example.test/")
            viewModel.connect()
            advanceUntilIdle()
            assertEquals(ServerConnectionStatus.Connected, viewModel.uiState.value.status)

            viewModel.returnToEditing()

            assertEquals(ServerConnectionStatus.Idle, viewModel.uiState.value.status)
            assertEquals("https://media.example.test/", viewModel.uiState.value.serverUrl)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `second connect is ignored while request is running`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val jellyfin = FakeJellyfinRepository(gate = gate)
            val viewModel = createViewModel(jellyfin = jellyfin)
            runCurrent()
            viewModel.onServerUrlChanged("https://media.example.test")

            viewModel.connect()
            viewModel.connect()
            runCurrent()

            assertEquals(1, jellyfin.calls)
            gate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `repository failures map to actionable UI errors`() =
        runTest(mainDispatcherRule.dispatcher) {
            val cases =
                listOf(
                    JellyfinConnectionResult.Unreachable to ServerConnectionUiError.Unreachable,
                    JellyfinConnectionResult.SecureConnectionFailed to ServerConnectionUiError.SecureConnection,
                    JellyfinConnectionResult.ServerRejected to ServerConnectionUiError.ServerRejected,
                    JellyfinConnectionResult.InvalidResponse to ServerConnectionUiError.InvalidResponse,
                    JellyfinConnectionResult.UnexpectedFailure to ServerConnectionUiError.Unexpected,
                )

            cases.forEach { (result, expectedError) ->
                val viewModel = createViewModel(jellyfin = FakeJellyfinRepository(result = result))
                runCurrent()
                viewModel.onServerUrlChanged("https://media.example.test")
                viewModel.connect()
                advanceUntilIdle()

                assertEquals(ServerConnectionStatus.Error, viewModel.uiState.value.status)
                assertEquals(expectedError, viewModel.uiState.value.error)
            }
        }

    private fun createViewModel(
        jellyfin: JellyfinRepository = FakeJellyfinRepository(),
        settings: ServerSettingsRepository = FakeServerSettingsRepository(),
    ) = ServerConnectionViewModel(
        jellyfinRepository = jellyfin,
        settingsRepository = settings,
        urlNormalizer = ServerUrlNormalizer(),
        ioDispatcher = mainDispatcherRule.dispatcher,
    )
}

private class FakeJellyfinRepository(
    private val result: JellyfinConnectionResult = JellyfinConnectionResult.Unreachable,
    private val gate: CompletableDeferred<Unit>? = null,
) : JellyfinRepository {
    var calls = 0
        private set

    override suspend fun connect(serverUrl: String): JellyfinConnectionResult {
        calls += 1
        gate?.await()
        return result
    }

    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthenticationResult = JellyfinAuthenticationResult.UnexpectedFailure
}

private class FakeServerSettingsRepository(initialUrl: String? = null) : ServerSettingsRepository {
    private val storedUrl = MutableStateFlow(initialUrl)
    override val serverUrl: Flow<String?> = storedUrl
    val savedUrls = mutableListOf<String>()

    override suspend fun saveServerUrl(serverUrl: String) {
        savedUrls += serverUrl
        storedUrl.value = serverUrl
    }
}
