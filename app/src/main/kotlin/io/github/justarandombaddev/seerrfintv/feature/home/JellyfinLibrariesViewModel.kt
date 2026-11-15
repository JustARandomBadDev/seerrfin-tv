package io.github.justarandombaddev.seerrfintv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.ServerSettingsRepository
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionRestoreResult
import io.github.justarandombaddev.seerrfintv.domain.session.JellyfinSessionStore
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class LibrariesStatus {
    Restoring,
    NeedsServer,
    NeedsLogin,
    Loading,
    Empty,
    Success,
    Error,
}

enum class LibrariesUiError {
    Unreachable,
    SecureConnection,
    ServerRejected,
    InvalidResponse,
    Unexpected,
}

enum class LibrariesSessionIssue {
    Expired,
    Unreadable,
}

data class JellyfinLibrariesUiState(
    val status: LibrariesStatus = LibrariesStatus.Restoring,
    val serverUrl: String? = null,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val error: LibrariesUiError? = null,
    val sessionIssue: LibrariesSessionIssue? = null,
    val loginRequestId: Int = 0,
) {
    val isLoading: Boolean
        get() = status == LibrariesStatus.Restoring || status == LibrariesStatus.Loading
}

class JellyfinLibrariesViewModel(
    private val jellyfinRepository: JellyfinRepository,
    private val settingsRepository: ServerSettingsRepository,
    private val sessionStore: JellyfinSessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(JellyfinLibrariesUiState())
    val uiState: StateFlow<JellyfinLibrariesUiState> = mutableUiState.asStateFlow()
    private var nextLoginRequestId = 0

    init {
        restoreAndLoad(serverUrl = null, initialLoad = true)
    }

    fun onServerConnected(serverUrl: String) {
        if (mutableUiState.value.isLoading) return
        showLogin(serverUrl, issue = null)
    }

    fun onLoginSucceeded() {
        val state = mutableUiState.value
        if (state.status != LibrariesStatus.NeedsLogin) return
        restoreAndLoad(state.serverUrl)
    }

    fun onLoginBack() {
        if (mutableUiState.value.isLoading) return
        mutableUiState.value = JellyfinLibrariesUiState(status = LibrariesStatus.NeedsServer)
    }

    fun retry() {
        val state = mutableUiState.value
        if (state.status != LibrariesStatus.Error && state.status != LibrariesStatus.Empty) return
        restoreAndLoad(state.serverUrl)
    }

    private fun restoreAndLoad(
        serverUrl: String?,
        initialLoad: Boolean = false,
    ) {
        if (!initialLoad && mutableUiState.value.isLoading) return
        mutableUiState.value =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.Restoring,
                serverUrl = serverUrl,
            )
        viewModelScope.launch(ioDispatcher) {
            val resolvedServerUrl = serverUrl ?: loadSavedServerUrl() ?: return@launch
            when (val restored = restoreSession()) {
                is JellyfinSessionRestoreResult.Restored -> loadLibraries(resolvedServerUrl, restored.session)
                JellyfinSessionRestoreResult.Missing -> showLogin(resolvedServerUrl, issue = null)
                JellyfinSessionRestoreResult.Unreadable ->
                    showLogin(resolvedServerUrl, LibrariesSessionIssue.Unreadable)
            }
        }
    }

    private suspend fun loadSavedServerUrl(): String? =
        try {
            settingsRepository.serverUrl.first().also { savedUrl ->
                if (savedUrl == null) {
                    mutableUiState.value = JellyfinLibrariesUiState(status = LibrariesStatus.NeedsServer)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            showError(serverUrl = null, LibrariesUiError.Unexpected)
            null
        }

    private suspend fun restoreSession(): JellyfinSessionRestoreResult =
        try {
            sessionStore.restore()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            clearSessionIgnoringFailure()
            JellyfinSessionRestoreResult.Unreadable
        }

    private suspend fun loadLibraries(
        serverUrl: String,
        session: JellyfinSession,
    ) {
        mutableUiState.value =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.Loading,
                serverUrl = serverUrl,
            )
        val result =
            try {
                jellyfinRepository.fetchLibraries(serverUrl, session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                JellyfinLibrariesResult.UnexpectedFailure
            }
        when (result) {
            is JellyfinLibrariesResult.Success -> {
                mutableUiState.value =
                    JellyfinLibrariesUiState(
                        status =
                            if (result.libraries.isEmpty()) {
                                LibrariesStatus.Empty
                            } else {
                                LibrariesStatus.Success
                            },
                        serverUrl = serverUrl,
                        libraries = result.libraries,
                    )
            }

            JellyfinLibrariesResult.InvalidSession -> {
                clearSessionIgnoringFailure()
                showLogin(serverUrl, LibrariesSessionIssue.Expired)
            }

            JellyfinLibrariesResult.Unreachable -> showError(serverUrl, LibrariesUiError.Unreachable)
            JellyfinLibrariesResult.SecureConnectionFailed ->
                showError(serverUrl, LibrariesUiError.SecureConnection)

            JellyfinLibrariesResult.ServerRejected -> showError(serverUrl, LibrariesUiError.ServerRejected)
            JellyfinLibrariesResult.InvalidResponse -> showError(serverUrl, LibrariesUiError.InvalidResponse)
            JellyfinLibrariesResult.UnexpectedFailure -> showError(serverUrl, LibrariesUiError.Unexpected)
        }
    }

    private suspend fun clearSessionIgnoringFailure() {
        try {
            sessionStore.clear()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // A replacement login can overwrite a stale encrypted record.
        }
    }

    private fun showLogin(
        serverUrl: String,
        issue: LibrariesSessionIssue?,
    ) {
        nextLoginRequestId += 1
        mutableUiState.value =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.NeedsLogin,
                serverUrl = serverUrl,
                sessionIssue = issue,
                loginRequestId = nextLoginRequestId,
            )
    }

    private fun showError(
        serverUrl: String?,
        error: LibrariesUiError,
    ) {
        mutableUiState.value =
            JellyfinLibrariesUiState(
                status = LibrariesStatus.Error,
                serverUrl = serverUrl,
                error = error,
            )
    }
}
