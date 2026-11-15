package io.github.justarandombaddev.seerrfintv.feature.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class JellyfinLoginStatus {
    Idle,
    Loading,
    Authenticated,
    Error,
}

enum class JellyfinLoginUiError {
    EmptyUsername,
    InvalidCredentials,
    Unreachable,
    SecureConnection,
    ServerRejected,
    InvalidResponse,
    SessionStorage,
    Unexpected,
}

data class JellyfinLoginUiState(
    val username: String = "",
    val password: String = "",
    val status: JellyfinLoginStatus = JellyfinLoginStatus.Idle,
    val error: JellyfinLoginUiError? = null,
    val displayName: String = "",
) {
    val isLoading: Boolean get() = status == JellyfinLoginStatus.Loading
    val isAuthenticated: Boolean get() = status == JellyfinLoginStatus.Authenticated

    override fun toString(): String =
        "JellyfinLoginUiState(status=$status, error=$error, credentials=<redacted>)"
}

class JellyfinLoginViewModel(
    private val serverUrl: String,
    private val jellyfinRepository: JellyfinRepository,
    private val sessionStore: JellyfinSessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(JellyfinLoginUiState())
    val uiState: StateFlow<JellyfinLoginUiState> = mutableUiState.asStateFlow()

    fun onUsernameChanged(username: String) {
        if (!canEdit()) return
        mutableUiState.update { state ->
            state.copy(
                username = username,
                status = JellyfinLoginStatus.Idle,
                error = null,
            )
        }
    }

    fun onPasswordChanged(password: String) {
        if (!canEdit()) return
        mutableUiState.update { state ->
            state.copy(
                password = password,
                status = JellyfinLoginStatus.Idle,
                error = null,
            )
        }
    }

    fun login() {
        val state = mutableUiState.value
        if (state.isLoading || state.isAuthenticated) return

        val username = state.username.trim()
        if (username.isEmpty()) {
            showError(JellyfinLoginUiError.EmptyUsername)
            return
        }

        mutableUiState.update { current ->
            current.copy(
                username = username,
                status = JellyfinLoginStatus.Loading,
                error = null,
            )
        }
        val password = state.password
        viewModelScope.launch(ioDispatcher) {
            val result =
                try {
                    jellyfinRepository.authenticate(serverUrl, username, password)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    JellyfinAuthenticationResult.UnexpectedFailure
                }
            applyAuthenticationResult(result)
        }
    }

    private suspend fun applyAuthenticationResult(result: JellyfinAuthenticationResult) {
        if (result is JellyfinAuthenticationResult.Authenticated) {
            mutableUiState.update { state -> state.copy(password = "") }
            try {
                sessionStore.save(result.session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                showError(JellyfinLoginUiError.SessionStorage)
                return
            }
            mutableUiState.update { state ->
                state.copy(
                    status = JellyfinLoginStatus.Authenticated,
                    error = null,
                    displayName = result.displayName,
                )
            }
            return
        }

        val error =
            when (result) {
                JellyfinAuthenticationResult.InvalidCredentials -> JellyfinLoginUiError.InvalidCredentials
                JellyfinAuthenticationResult.Unreachable -> JellyfinLoginUiError.Unreachable
                JellyfinAuthenticationResult.SecureConnectionFailed -> JellyfinLoginUiError.SecureConnection
                JellyfinAuthenticationResult.ServerRejected -> JellyfinLoginUiError.ServerRejected
                JellyfinAuthenticationResult.InvalidResponse -> JellyfinLoginUiError.InvalidResponse
                JellyfinAuthenticationResult.UnexpectedFailure -> JellyfinLoginUiError.Unexpected
                is JellyfinAuthenticationResult.Authenticated -> error("Handled above")
            }
        showError(error)
    }

    private fun canEdit(): Boolean =
        !mutableUiState.value.isLoading && !mutableUiState.value.isAuthenticated

    private fun showError(error: JellyfinLoginUiError) {
        mutableUiState.update { state ->
            state.copy(
                status = JellyfinLoginStatus.Error,
                error = error,
            )
        }
    }
}
