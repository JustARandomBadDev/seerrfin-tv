package io.github.justarandombaddev.seerrfintv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinHomeRow
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinHomeResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
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
import kotlinx.coroutines.withContext

private val SUPPORTED_CATALOG_COLLECTION_TYPES = setOf("MOVIES", "TVSHOWS", "HOMEVIDEOS")

internal fun supportedCatalogLibraries(libraries: List<JellyfinLibrary>): List<JellyfinLibrary> =
    libraries.filter { library -> library.collectionType in SUPPORTED_CATALOG_COLLECTION_TYPES }

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

enum class PlaybackResolutionStatus {
    Idle,
    Loading,
    Ready,
    Empty,
    Error,
}

data class JellyfinLibrariesUiState(
    val status: LibrariesStatus = LibrariesStatus.Restoring,
    val serverUrl: String? = null,
    val libraries: List<JellyfinLibrary> = emptyList(),
    val homeRows: List<JellyfinHomeRow> = emptyList(),
    val selectedLibraryId: String? = null,
    val error: LibrariesUiError? = null,
    val sessionIssue: LibrariesSessionIssue? = null,
    val loginRequestId: Int = 0,
    val playbackStatus: PlaybackResolutionStatus = PlaybackResolutionStatus.Idle,
    val activeLibraryId: String? = null,
    val activeItemId: String? = null,
    val playbackSource: JellyfinPlaybackSource? = null,
    val playbackError: LibrariesUiError? = null,
) {
    val isLoading: Boolean
        get() = status == LibrariesStatus.Restoring || status == LibrariesStatus.Loading

    val visibleHomeRows: List<JellyfinHomeRow>
        get() = selectedLibraryId?.let { selected -> homeRows.filter { it.library.id == selected } } ?: homeRows
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
    private var activeSession: JellyfinSession? = null

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

    fun selectLibrary(libraryId: String?) {
        val state = mutableUiState.value
        if (state.status != LibrariesStatus.Success) return
        if (libraryId != null && state.libraries.none { it.id == libraryId }) return
        mutableUiState.value = state.copy(selectedLibraryId = libraryId)
    }

    fun resolveLibrary(libraryId: String) {
        val state = mutableUiState.value
        val serverUrl = state.serverUrl ?: return
        val session = activeSession ?: return
        if (
            state.status != LibrariesStatus.Success ||
            state.playbackStatus == PlaybackResolutionStatus.Loading ||
            state.libraries.none { it.id == libraryId }
        ) {
            return
        }
        mutableUiState.value =
            state.copy(
                playbackStatus = PlaybackResolutionStatus.Loading,
                activeLibraryId = libraryId,
                playbackSource = null,
                playbackError = null,
            )
        viewModelScope.launch(ioDispatcher) {
            val result =
                try {
                    jellyfinRepository.resolvePlaybackSource(serverUrl, session, libraryId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    JellyfinPlaybackResolutionResult.UnexpectedFailure
                }
            showPlaybackResolution(result)
        }
    }

    fun resolveItem(item: JellyfinCatalogItem) {
        if (item.type == JellyfinCatalogItemType.Series) return
        val state = mutableUiState.value
        val serverUrl = state.serverUrl ?: return
        val session = activeSession ?: return
        if (
            state.status != LibrariesStatus.Success ||
            state.playbackStatus == PlaybackResolutionStatus.Loading ||
            state.homeRows.none { row -> row.items.any { it.id == item.id } }
        ) {
            return
        }
        mutableUiState.value =
            state.copy(
                playbackStatus = PlaybackResolutionStatus.Loading,
                activeLibraryId = null,
                activeItemId = item.id,
                playbackSource = null,
                playbackError = null,
            )
        viewModelScope.launch(ioDispatcher) {
            val result =
                try {
                    jellyfinRepository.resolvePlaybackItem(serverUrl, session, item)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    JellyfinPlaybackResolutionResult.UnexpectedFailure
                }
            showPlaybackResolution(result)
        }
    }

    fun closePlayer() {
        val state = mutableUiState.value
        if (state.status != LibrariesStatus.Success || state.playbackSource == null) return
        mutableUiState.value =
            state.copy(
                playbackStatus = PlaybackResolutionStatus.Idle,
                activeLibraryId = null,
                activeItemId = null,
                playbackSource = null,
                playbackError = null,
            )
    }

    suspend fun reportPlayback(report: JellyfinPlaybackReport) {
        val serverUrl = mutableUiState.value.serverUrl ?: return
        val session = activeSession ?: return
        try {
            withContext(ioDispatcher) {
                jellyfinRepository.reportPlayback(serverUrl, session, report)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Reporting is best effort and must not interrupt active playback.
        }
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
                is JellyfinSessionRestoreResult.Restored -> {
                    activeSession = restored.session
                    loadLibraries(resolvedServerUrl, restored.session)
                }
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
                val supportedLibraries = supportedCatalogLibraries(result.libraries)
                if (supportedLibraries.isEmpty()) {
                    mutableUiState.value =
                        JellyfinLibrariesUiState(
                            status = LibrariesStatus.Empty,
                            serverUrl = serverUrl,
                            libraries = emptyList(),
                        )
                } else {
                    loadHomeRows(serverUrl, session, supportedLibraries)
                }
            }

            JellyfinLibrariesResult.InvalidSession -> {
                activeSession = null
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

    private suspend fun loadHomeRows(
        serverUrl: String,
        session: JellyfinSession,
        libraries: List<JellyfinLibrary>,
    ) {
        val result =
            try {
                jellyfinRepository.fetchHomeRows(serverUrl, session, libraries)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                JellyfinHomeResult.UnexpectedFailure
            }
        when (result) {
            is JellyfinHomeResult.Success ->
                mutableUiState.value =
                    JellyfinLibrariesUiState(
                        status = if (result.rows.isEmpty()) LibrariesStatus.Empty else LibrariesStatus.Success,
                        serverUrl = serverUrl,
                        libraries = if (result.rows.isEmpty()) libraries else result.rows.map(JellyfinHomeRow::library),
                        homeRows = result.rows,
                    )

            JellyfinHomeResult.InvalidSession -> {
                activeSession = null
                clearSessionIgnoringFailure()
                showLogin(serverUrl, LibrariesSessionIssue.Expired)
            }

            JellyfinHomeResult.Unreachable -> showError(serverUrl, LibrariesUiError.Unreachable)
            JellyfinHomeResult.SecureConnectionFailed ->
                showError(serverUrl, LibrariesUiError.SecureConnection)
            JellyfinHomeResult.ServerRejected -> showError(serverUrl, LibrariesUiError.ServerRejected)
            JellyfinHomeResult.InvalidResponse -> showError(serverUrl, LibrariesUiError.InvalidResponse)
            JellyfinHomeResult.UnexpectedFailure -> showError(serverUrl, LibrariesUiError.Unexpected)
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

    private suspend fun showPlaybackResolution(result: JellyfinPlaybackResolutionResult) {
        when (result) {
            is JellyfinPlaybackResolutionResult.Success ->
                mutableUiState.value =
                    mutableUiState.value.copy(
                        playbackStatus = PlaybackResolutionStatus.Ready,
                        playbackSource = result.source,
                        playbackError = null,
                    )

            JellyfinPlaybackResolutionResult.Empty ->
                mutableUiState.value =
                    mutableUiState.value.copy(
                        playbackStatus = PlaybackResolutionStatus.Empty,
                        playbackSource = null,
                        playbackError = null,
                    )

            JellyfinPlaybackResolutionResult.InvalidSession -> {
                activeSession = null
                clearSessionIgnoringFailure()
                val serverUrl = mutableUiState.value.serverUrl ?: return
                showLogin(serverUrl, LibrariesSessionIssue.Expired)
            }

            JellyfinPlaybackResolutionResult.Unreachable ->
                showPlaybackError(LibrariesUiError.Unreachable)

            JellyfinPlaybackResolutionResult.SecureConnectionFailed ->
                showPlaybackError(LibrariesUiError.SecureConnection)

            JellyfinPlaybackResolutionResult.ServerRejected ->
                showPlaybackError(LibrariesUiError.ServerRejected)

            JellyfinPlaybackResolutionResult.InvalidResponse ->
                showPlaybackError(LibrariesUiError.InvalidResponse)

            JellyfinPlaybackResolutionResult.UnexpectedFailure ->
                showPlaybackError(LibrariesUiError.Unexpected)
        }
    }

    private fun showPlaybackError(error: LibrariesUiError) {
        mutableUiState.value =
            mutableUiState.value.copy(
                playbackStatus = PlaybackResolutionStatus.Error,
                playbackSource = null,
                playbackError = error,
            )
    }

    private fun showLogin(
        serverUrl: String,
        issue: LibrariesSessionIssue?,
    ) {
        activeSession = null
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
