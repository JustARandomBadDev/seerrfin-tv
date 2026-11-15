package io.github.justarandombaddev.seerrfintv.feature.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlError
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalization
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ServerConnectionStatus {
    Idle,
    Loading,
    Connected,
    Error,
}

enum class ServerConnectionUiError {
    EmptyAddress,
    InvalidAddress,
    UnsupportedScheme,
    CredentialsNotAllowed,
    QueryOrFragmentNotAllowed,
    Unreachable,
    SecureConnection,
    ServerRejected,
    InvalidResponse,
    SettingsFailure,
    Unexpected,
}

data class ServerConnectionUiState(
    val serverUrl: String = "",
    val status: ServerConnectionStatus = ServerConnectionStatus.Idle,
    val error: ServerConnectionUiError? = null,
) {
    val isLoading: Boolean get() = status == ServerConnectionStatus.Loading
}

class ServerConnectionViewModel(
    private val jellyfinRepository: JellyfinRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val urlNormalizer: ServerUrlNormalizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ServerConnectionUiState())
    val uiState: StateFlow<ServerConnectionUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val savedUrl = settingsRepository.serverUrl.first()
            if (savedUrl != null && mutableUiState.value.serverUrl.isBlank()) {
                mutableUiState.update { state -> state.copy(serverUrl = savedUrl) }
            }
        }
    }

    fun onServerUrlChanged(serverUrl: String) {
        mutableUiState.update { state ->
            state.copy(
                serverUrl = serverUrl,
                status = ServerConnectionStatus.Idle,
                error = null,
            )
        }
    }

    fun connect() {
        if (mutableUiState.value.isLoading) return

        when (val normalized = urlNormalizer.normalize(mutableUiState.value.serverUrl)) {
            is ServerUrlNormalization.Invalid -> showValidationError(normalized.reason)
            is ServerUrlNormalization.Valid -> connectTo(normalized.url)
        }
    }

    private fun connectTo(serverUrl: String) {
        mutableUiState.update { state ->
            state.copy(
                serverUrl = serverUrl,
                status = ServerConnectionStatus.Loading,
                error = null,
            )
        }

        viewModelScope.launch(ioDispatcher) {
            val result =
                try {
                    jellyfinRepository.connect(serverUrl)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    JellyfinConnectionResult.UnexpectedFailure
                }
            applyConnectionResult(result)
        }
    }

    private suspend fun applyConnectionResult(result: JellyfinConnectionResult) {
        if (result is JellyfinConnectionResult.Connected) {
            try {
                settingsRepository.saveServerUrl(result.serverUrl)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError(ServerConnectionUiError.SettingsFailure)
                return
            }
            mutableUiState.update { state ->
                state.copy(
                    serverUrl = result.serverUrl,
                    status = ServerConnectionStatus.Connected,
                    error = null,
                )
            }
            return
        }

        val error =
            when (result) {
                JellyfinConnectionResult.Unreachable -> ServerConnectionUiError.Unreachable
                JellyfinConnectionResult.SecureConnectionFailed -> ServerConnectionUiError.SecureConnection
                JellyfinConnectionResult.ServerRejected -> ServerConnectionUiError.ServerRejected
                JellyfinConnectionResult.InvalidResponse -> ServerConnectionUiError.InvalidResponse
                JellyfinConnectionResult.UnexpectedFailure -> ServerConnectionUiError.Unexpected
                is JellyfinConnectionResult.Connected -> error("Handled above")
            }
        showError(error)
    }

    private fun showValidationError(error: ServerUrlError) {
        val uiError =
            when (error) {
                ServerUrlError.Empty -> ServerConnectionUiError.EmptyAddress
                ServerUrlError.InvalidAddress -> ServerConnectionUiError.InvalidAddress
                ServerUrlError.UnsupportedScheme -> ServerConnectionUiError.UnsupportedScheme
                ServerUrlError.CredentialsNotAllowed -> ServerConnectionUiError.CredentialsNotAllowed
                ServerUrlError.QueryOrFragmentNotAllowed -> ServerConnectionUiError.QueryOrFragmentNotAllowed
            }
        showError(uiError)
    }

    private fun showError(error: ServerConnectionUiError) {
        mutableUiState.update { state ->
            state.copy(
                status = ServerConnectionStatus.Error,
                error = error,
            )
        }
    }
}
