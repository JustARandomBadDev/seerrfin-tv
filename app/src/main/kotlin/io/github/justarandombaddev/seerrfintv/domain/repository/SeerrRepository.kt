package io.github.justarandombaddev.seerrfintv.domain.repository

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSession
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
