package io.github.justarandombaddev.seerrfintv.feature.seerr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalization
import io.github.justarandombaddev.seerrfintv.data.jellyfin.ServerUrlNormalizer
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrUser
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectCheckResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectInitiationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSessionValidationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.SeerrSessionStore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SeerrAuthStatus {
    Restoring,
    Editing,
    Initiating,
    WaitingForAuthorization,
    Authenticating,
    Authenticated,
    Error,
}

enum class SeerrAuthError {
    InvalidServerAddress,
    QuickConnectUnavailable,
    QuickConnectExpired,
    QuickConnectTimedOut,
    AccessDenied,
    SessionExpired,
    SessionUnreadable,
    Unreachable,
    SecureConnection,
    ServerRejected,
    InvalidResponse,
    SessionStorage,
    Unexpected,
}

data class SeerrAuthUiState(
    val status: SeerrAuthStatus = SeerrAuthStatus.Restoring,
    val serverUrl: String = "",
    val code: String? = null,
    val user: SeerrUser? = null,
    val error: SeerrAuthError? = null,
) {
    val canEditServerUrl: Boolean
        get() = status == SeerrAuthStatus.Editing || status == SeerrAuthStatus.Error

    override fun toString(): String =
        "SeerrAuthUiState(status=$status, serverUrl=<redacted>, code=$code, user=$user, error=$error)"
}

class SeerrAuthViewModel(
    private val repository: SeerrRepository,
    private val sessionStore: SeerrSessionStore,
    private val settingsRepository: SeerrSettingsRepository,
    private val urlNormalizer: ServerUrlNormalizer,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val pollIntervalMillis: Long = 2_000L,
    private val maxPollAttempts: Int = 150,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(SeerrAuthUiState())
    val uiState: StateFlow<SeerrAuthUiState> = mutableUiState.asStateFlow()
    private var quickConnectJob: Job? = null

    init {
        restore()
    }

    fun onServerUrlChanged(serverUrl: String) {
        if (!mutableUiState.value.canEditServerUrl) return
        mutableUiState.update { state ->
            state.copy(
                status = SeerrAuthStatus.Editing,
                serverUrl = serverUrl,
                code = null,
                user = null,
                error = null,
            )
        }
    }

    fun connect() {
        if (!mutableUiState.value.canEditServerUrl || quickConnectJob?.isActive == true) return
        val normalized = urlNormalizer.normalize(mutableUiState.value.serverUrl)
        if (normalized !is ServerUrlNormalization.Valid) {
            showError(SeerrAuthError.InvalidServerAddress)
            return
        }
        mutableUiState.update { state ->
            state.copy(
                status = SeerrAuthStatus.Initiating,
                serverUrl = normalized.url,
                code = null,
                user = null,
                error = null,
            )
        }
        quickConnectJob =
            viewModelScope.launch(ioDispatcher) {
                val result = safeInitiate(normalized.url)
                when (result) {
                    is SeerrQuickConnectInitiationResult.Ready -> {
                        try {
                            settingsRepository.saveServerUrl(normalized.url)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            showError(SeerrAuthError.SessionStorage)
                            return@launch
                        }
                        mutableUiState.update { state ->
                            state.copy(
                                status = SeerrAuthStatus.WaitingForAuthorization,
                                code = result.challenge.code,
                                error = null,
                            )
                        }
                        pollAuthorization(normalized.url, result.challenge.secret)
                    }

                    SeerrQuickConnectInitiationResult.Unavailable ->
                        showError(SeerrAuthError.QuickConnectUnavailable)
                    SeerrQuickConnectInitiationResult.Unreachable -> showError(SeerrAuthError.Unreachable)
                    SeerrQuickConnectInitiationResult.SecureConnectionFailed ->
                        showError(SeerrAuthError.SecureConnection)
                    SeerrQuickConnectInitiationResult.ServerRejected ->
                        showError(SeerrAuthError.ServerRejected)
                    SeerrQuickConnectInitiationResult.InvalidResponse ->
                        showError(SeerrAuthError.InvalidResponse)
                }
            }
    }

    fun stop() {
        quickConnectJob?.cancel()
        quickConnectJob = null
        mutableUiState.update { state ->
            if (
                state.status == SeerrAuthStatus.WaitingForAuthorization ||
                state.status == SeerrAuthStatus.Authenticating ||
                state.status == SeerrAuthStatus.Initiating
            ) {
                state.copy(status = SeerrAuthStatus.Editing, code = null, error = null)
            } else {
                state
            }
        }
    }

    private fun restore() {
        viewModelScope.launch(ioDispatcher) {
            val savedUrl =
                try {
                    settingsRepository.serverUrl.first().orEmpty()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    ""
                }
            when (val restored = sessionStore.restore()) {
                SeerrSessionRestoreResult.Missing -> {
                    mutableUiState.value =
                        SeerrAuthUiState(status = SeerrAuthStatus.Editing, serverUrl = savedUrl)
                }

                SeerrSessionRestoreResult.Unreadable -> {
                    mutableUiState.value =
                        SeerrAuthUiState(
                            status = SeerrAuthStatus.Editing,
                            serverUrl = savedUrl,
                            error = SeerrAuthError.SessionUnreadable,
                        )
                }

                is SeerrSessionRestoreResult.Restored -> validateRestoredSession(restored, savedUrl)
            }
        }
    }

    private suspend fun validateRestoredSession(
        restored: SeerrSessionRestoreResult.Restored,
        savedUrl: String,
    ) {
        val session = restored.session
        val serverUrl = session.serverUrl.ifBlank { savedUrl }
        when (val result = safeValidate(session)) {
            is SeerrSessionValidationResult.Authenticated -> {
                mutableUiState.value =
                    SeerrAuthUiState(
                        status = SeerrAuthStatus.Authenticated,
                        serverUrl = serverUrl,
                        user = result.user,
                    )
            }

            SeerrSessionValidationResult.Expired -> {
                clearSessionBestEffort()
                mutableUiState.value =
                    SeerrAuthUiState(
                        status = SeerrAuthStatus.Editing,
                        serverUrl = serverUrl,
                        error = SeerrAuthError.SessionExpired,
                    )
            }

            SeerrSessionValidationResult.Unreachable ->
                showRestoreError(serverUrl, SeerrAuthError.Unreachable)
            SeerrSessionValidationResult.SecureConnectionFailed ->
                showRestoreError(serverUrl, SeerrAuthError.SecureConnection)
            SeerrSessionValidationResult.ServerRejected ->
                showRestoreError(serverUrl, SeerrAuthError.ServerRejected)
            SeerrSessionValidationResult.InvalidResponse -> {
                clearSessionBestEffort()
                showRestoreError(serverUrl, SeerrAuthError.InvalidResponse)
            }
        }
    }

    private suspend fun pollAuthorization(
        serverUrl: String,
        secret: String,
    ) {
        repeat(maxPollAttempts) {
            delay(pollIntervalMillis)
            when (val check = safeCheck(serverUrl, secret)) {
                SeerrQuickConnectCheckResult.Pending -> Unit
                SeerrQuickConnectCheckResult.Authorized -> {
                    authenticate(serverUrl, secret)
                    return
                }

                SeerrQuickConnectCheckResult.Expired -> {
                    showError(SeerrAuthError.QuickConnectExpired)
                    return
                }

                SeerrQuickConnectCheckResult.Unavailable -> {
                    showError(SeerrAuthError.QuickConnectUnavailable)
                    return
                }

                SeerrQuickConnectCheckResult.Unreachable -> {
                    showError(SeerrAuthError.Unreachable)
                    return
                }

                SeerrQuickConnectCheckResult.SecureConnectionFailed -> {
                    showError(SeerrAuthError.SecureConnection)
                    return
                }

                SeerrQuickConnectCheckResult.ServerRejected -> {
                    showError(SeerrAuthError.ServerRejected)
                    return
                }

                SeerrQuickConnectCheckResult.InvalidResponse -> {
                    showError(SeerrAuthError.InvalidResponse)
                    return
                }
            }
        }
        showError(SeerrAuthError.QuickConnectTimedOut)
    }

    private suspend fun authenticate(
        serverUrl: String,
        secret: String,
    ) {
        mutableUiState.update { state -> state.copy(status = SeerrAuthStatus.Authenticating) }
        when (val result = safeAuthenticate(serverUrl, secret)) {
            is SeerrAuthenticationResult.Authenticated -> {
                try {
                    sessionStore.save(result.session)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    showError(SeerrAuthError.SessionStorage)
                    return
                }
                mutableUiState.update { state ->
                    state.copy(
                        status = SeerrAuthStatus.Authenticated,
                        code = null,
                        user = result.user,
                        error = null,
                    )
                }
            }

            SeerrAuthenticationResult.AccessDenied -> showError(SeerrAuthError.AccessDenied)
            SeerrAuthenticationResult.Unreachable -> showError(SeerrAuthError.Unreachable)
            SeerrAuthenticationResult.SecureConnectionFailed ->
                showError(SeerrAuthError.SecureConnection)
            SeerrAuthenticationResult.ServerRejected -> showError(SeerrAuthError.ServerRejected)
            SeerrAuthenticationResult.InvalidResponse -> showError(SeerrAuthError.InvalidResponse)
        }
    }

    private suspend fun safeInitiate(serverUrl: String): SeerrQuickConnectInitiationResult =
        try {
            repository.initiateQuickConnect(serverUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SeerrQuickConnectInitiationResult.InvalidResponse
        }

    private suspend fun safeCheck(
        serverUrl: String,
        secret: String,
    ): SeerrQuickConnectCheckResult =
        try {
            repository.checkQuickConnect(serverUrl, secret)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SeerrQuickConnectCheckResult.InvalidResponse
        }

    private suspend fun safeAuthenticate(
        serverUrl: String,
        secret: String,
    ): SeerrAuthenticationResult =
        try {
            repository.authenticateQuickConnect(serverUrl, secret)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SeerrAuthenticationResult.InvalidResponse
        }

    private suspend fun safeValidate(
        session: io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession,
    ): SeerrSessionValidationResult =
        try {
            repository.validateSession(session)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            SeerrSessionValidationResult.InvalidResponse
        }

    private suspend fun clearSessionBestEffort() {
        try {
            sessionStore.clear()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // An expired or invalid session is never reused when cleanup fails.
        }
    }

    private fun showRestoreError(
        serverUrl: String,
        error: SeerrAuthError,
    ) {
        mutableUiState.value =
            SeerrAuthUiState(
                status = SeerrAuthStatus.Error,
                serverUrl = serverUrl,
                error = error,
            )
    }

    private fun showError(error: SeerrAuthError) {
        mutableUiState.update { state ->
            state.copy(
                status = SeerrAuthStatus.Error,
                code = null,
                user = null,
                error = error,
            )
        }
    }
}
