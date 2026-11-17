package io.github.justarandombaddev.seerrfintv.domain.repository

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaRequest
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaSummary
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaType
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMovieDetails
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrPagedResult
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestFilter
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestPage
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestSort
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSortDirection
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTimeWindow
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTvDetails
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTvSeasonSelection
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrUser

data class SeerrQuickConnectChallenge(
    val code: String,
    val secret: String,
) {
    override fun toString(): String = "SeerrQuickConnectChallenge(code=$code, secret=<redacted>)"
}

sealed interface SeerrQuickConnectInitiationResult {
    data class Ready(val challenge: SeerrQuickConnectChallenge) : SeerrQuickConnectInitiationResult

    data object Unavailable : SeerrQuickConnectInitiationResult

    data object Unreachable : SeerrQuickConnectInitiationResult

    data object SecureConnectionFailed : SeerrQuickConnectInitiationResult

    data object ServerRejected : SeerrQuickConnectInitiationResult

    data object InvalidResponse : SeerrQuickConnectInitiationResult
}

sealed interface SeerrQuickConnectCheckResult {
    data object Pending : SeerrQuickConnectCheckResult

    data object Authorized : SeerrQuickConnectCheckResult

    data object Expired : SeerrQuickConnectCheckResult

    data object Unavailable : SeerrQuickConnectCheckResult

    data object Unreachable : SeerrQuickConnectCheckResult

    data object SecureConnectionFailed : SeerrQuickConnectCheckResult

    data object ServerRejected : SeerrQuickConnectCheckResult

    data object InvalidResponse : SeerrQuickConnectCheckResult
}

sealed interface SeerrAuthenticationResult {
    data class Authenticated(
        val session: SeerrSession,
        val user: SeerrUser,
    ) : SeerrAuthenticationResult

    data object AccessDenied : SeerrAuthenticationResult

    data object Unreachable : SeerrAuthenticationResult

    data object SecureConnectionFailed : SeerrAuthenticationResult

    data object ServerRejected : SeerrAuthenticationResult

    data object InvalidResponse : SeerrAuthenticationResult
}

sealed interface SeerrSessionValidationResult {
    data class Authenticated(val user: SeerrUser) : SeerrSessionValidationResult

    data object Expired : SeerrSessionValidationResult

    data object Unreachable : SeerrSessionValidationResult

    data object SecureConnectionFailed : SeerrSessionValidationResult

    data object ServerRejected : SeerrSessionValidationResult

    data object InvalidResponse : SeerrSessionValidationResult
}

sealed interface SeerrApiResult<out T> {
    data class Success<T>(val value: T) : SeerrApiResult<T>

    data object SessionExpired : SeerrApiResult<Nothing>

    data object PermissionDenied : SeerrApiResult<Nothing>

    data object NotFound : SeerrApiResult<Nothing>

    data object Unreachable : SeerrApiResult<Nothing>

    data object SecureConnectionFailed : SeerrApiResult<Nothing>

    data object ServerRejected : SeerrApiResult<Nothing>

    data object InvalidResponse : SeerrApiResult<Nothing>
}

interface SeerrRepository {
    suspend fun initiateQuickConnect(serverUrl: String): SeerrQuickConnectInitiationResult

    suspend fun checkQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrQuickConnectCheckResult

    suspend fun authenticateQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrAuthenticationResult

    suspend fun validateSession(session: SeerrSession): SeerrSessionValidationResult
}

interface SeerrApiRepository {
    suspend fun discoverMovies(
        session: SeerrSession,
        page: Int = 1,
        language: String? = null,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>>

    suspend fun discoverSeries(
        session: SeerrSession,
        page: Int = 1,
        language: String? = null,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>>

    suspend fun discoverTrending(
        session: SeerrSession,
        page: Int = 1,
        language: String? = null,
        mediaType: SeerrMediaType = SeerrMediaType.ALL,
        timeWindow: SeerrTimeWindow = SeerrTimeWindow.DAY,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>>

    suspend fun search(
        session: SeerrSession,
        query: String,
        page: Int = 1,
        language: String? = null,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>>

    suspend fun movieDetails(
        session: SeerrSession,
        tmdbId: Long,
        language: String? = null,
    ): SeerrApiResult<SeerrMovieDetails>

    suspend fun tvDetails(
        session: SeerrSession,
        tmdbId: Long,
        language: String? = null,
    ): SeerrApiResult<SeerrTvDetails>

    suspend fun requests(
        session: SeerrSession,
        take: Int? = null,
        skip: Int? = null,
        filter: SeerrRequestFilter? = null,
        sort: SeerrRequestSort = SeerrRequestSort.ADDED,
        sortDirection: SeerrSortDirection = SeerrSortDirection.DESCENDING,
        requestedBy: Long? = null,
        mediaType: SeerrMediaType = SeerrMediaType.ALL,
    ): SeerrApiResult<SeerrRequestPage>

    suspend fun createMovieRequest(
        session: SeerrSession,
        tmdbId: Long,
        is4k: Boolean = false,
    ): SeerrApiResult<SeerrMediaRequest>

    suspend fun createTvRequest(
        session: SeerrSession,
        tmdbId: Long,
        seasons: SeerrTvSeasonSelection,
        is4k: Boolean = false,
    ): SeerrApiResult<SeerrMediaRequest>
}
