package io.github.justarandombaddev.seerrfintv.domain.repository

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession

interface JellyfinRepository {
    suspend fun connect(serverUrl: String): JellyfinConnectionResult

    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthenticationResult

    suspend fun fetchLibraries(
        serverUrl: String,
        session: JellyfinSession,
    ): JellyfinLibrariesResult

    suspend fun resolvePlaybackSource(
        serverUrl: String,
        session: JellyfinSession,
        libraryId: String,
    ): JellyfinPlaybackResolutionResult
}

sealed interface JellyfinPlaybackResolutionResult {
    data class Success(val source: JellyfinPlaybackSource) : JellyfinPlaybackResolutionResult

    data object Empty : JellyfinPlaybackResolutionResult

    data object InvalidSession : JellyfinPlaybackResolutionResult

    data object Unreachable : JellyfinPlaybackResolutionResult

    data object SecureConnectionFailed : JellyfinPlaybackResolutionResult

    data object ServerRejected : JellyfinPlaybackResolutionResult

    data object InvalidResponse : JellyfinPlaybackResolutionResult

    data object UnexpectedFailure : JellyfinPlaybackResolutionResult
}

sealed interface JellyfinLibrariesResult {
    data class Success(val libraries: List<JellyfinLibrary>) : JellyfinLibrariesResult

    data object InvalidSession : JellyfinLibrariesResult

    data object Unreachable : JellyfinLibrariesResult

    data object SecureConnectionFailed : JellyfinLibrariesResult

    data object ServerRejected : JellyfinLibrariesResult

    data object InvalidResponse : JellyfinLibrariesResult

    data object UnexpectedFailure : JellyfinLibrariesResult
}

sealed interface JellyfinAuthenticationResult {
    data class Authenticated(
        val session: JellyfinSession,
        val displayName: String,
    ) : JellyfinAuthenticationResult

    data object InvalidCredentials : JellyfinAuthenticationResult

    data object Unreachable : JellyfinAuthenticationResult

    data object SecureConnectionFailed : JellyfinAuthenticationResult

    data object ServerRejected : JellyfinAuthenticationResult

    data object InvalidResponse : JellyfinAuthenticationResult

    data object UnexpectedFailure : JellyfinAuthenticationResult
}

sealed interface JellyfinConnectionResult {
    data class Connected(val serverUrl: String) : JellyfinConnectionResult

    data object Unreachable : JellyfinConnectionResult

    data object SecureConnectionFailed : JellyfinConnectionResult

    data object ServerRejected : JellyfinConnectionResult

    data object InvalidResponse : JellyfinConnectionResult

    data object UnexpectedFailure : JellyfinConnectionResult
}
