package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo

fun interface JellyfinServerProbe {
    suspend fun getPublicSystemInfo(serverUrl: String): PublicSystemInfo
}

data class SdkAuthenticationSession(
    val accessToken: String,
    val userId: String,
    val serverId: String,
    val displayName: String,
) {
    override fun toString(): String = "SdkAuthenticationSession(<redacted>)"
}

fun interface JellyfinUserAuthenticator {
    suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): SdkAuthenticationSession
}

class SdkJellyfinRepository internal constructor(
    private val probe: JellyfinServerProbe,
    private val authenticator: JellyfinUserAuthenticator,
) : JellyfinRepository {
    constructor(jellyfin: Jellyfin) : this(
        probe = createProbe(jellyfin),
        authenticator = createAuthenticator(jellyfin),
    )

    override suspend fun connect(serverUrl: String): JellyfinConnectionResult =
        try {
            val systemInfo = probe.getPublicSystemInfo(serverUrl)
            if (systemInfo.isJellyfinServer()) {
                JellyfinConnectionResult.Connected(serverUrl)
            } else {
                JellyfinConnectionResult.InvalidResponse
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: TimeoutException) {
            JellyfinConnectionResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinConnectionResult.SecureConnectionFailed
        } catch (_: InvalidStatusException) {
            JellyfinConnectionResult.ServerRejected
        } catch (_: InvalidContentException) {
            JellyfinConnectionResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinConnectionResult.UnexpectedFailure
        }

    override suspend fun authenticate(
        serverUrl: String,
        username: String,
        password: String,
    ): JellyfinAuthenticationResult =
        try {
            val authenticated = authenticator.authenticate(serverUrl, username, password)
            if (authenticated.hasRequiredValues()) {
                JellyfinAuthenticationResult.Authenticated(
                    session =
                        JellyfinSession(
                            accessToken = authenticated.accessToken,
                            userId = authenticated.userId,
                            serverId = authenticated.serverId,
                        ),
                    displayName = authenticated.displayName,
                )
            } else {
                JellyfinAuthenticationResult.InvalidResponse
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) {
                JellyfinAuthenticationResult.InvalidCredentials
            } else {
                JellyfinAuthenticationResult.ServerRejected
            }
        } catch (_: TimeoutException) {
            JellyfinAuthenticationResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinAuthenticationResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinAuthenticationResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinAuthenticationResult.UnexpectedFailure
        }

    private fun PublicSystemInfo.isJellyfinServer(): Boolean =
        productName.equals(JELLYFIN_PRODUCT_NAME, ignoreCase = true) && !version.isNullOrBlank()

    private fun SdkAuthenticationSession.hasRequiredValues(): Boolean =
        accessToken.isNotBlank() &&
            userId.isNotBlank() &&
            serverId.isNotBlank() &&
            displayName.isNotBlank()

    private companion object {
        const val JELLYFIN_PRODUCT_NAME = "Jellyfin Server"
        const val HTTP_UNAUTHORIZED = 401

        val HTTP_CLIENT_OPTIONS =
            HttpClientOptions(
                connectTimeout = 6.seconds,
                requestTimeout = 15.seconds,
                socketTimeout = 15.seconds,
            )

        fun createProbe(jellyfin: Jellyfin): JellyfinServerProbe =
            JellyfinServerProbe { serverUrl ->
                jellyfin
                    .createApi(
                        baseUrl = serverUrl,
                        httpClientOptions = HTTP_CLIENT_OPTIONS,
                    ).systemApi
                    .getPublicSystemInfo()
                    .content
            }

        fun createAuthenticator(jellyfin: Jellyfin): JellyfinUserAuthenticator =
            JellyfinUserAuthenticator { serverUrl, username, password ->
                val authentication =
                    jellyfin
                        .createApi(
                            baseUrl = serverUrl,
                            httpClientOptions = HTTP_CLIENT_OPTIONS,
                        ).authenticationApi
                        .authenticateUserByName(username = username, password = password)
                        .content
                SdkAuthenticationSession(
                    accessToken = authentication.accessToken.orEmpty(),
                    userId = authentication.user?.id?.toString().orEmpty(),
                    serverId = authentication.serverId ?: authentication.user?.serverId.orEmpty(),
                    displayName = authentication.user?.name?.takeIf(String::isNotBlank) ?: username,
                )
            }
    }
}
