package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.util.concurrent.CancellationException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
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
import org.jellyfin.sdk.api.client.extensions.userViewApi
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

data class SdkJellyfinLibrary(
    val id: String,
    val name: String,
    val collectionType: String?,
)

fun interface JellyfinLibraryProvider {
    suspend fun getUserViews(
        serverUrl: String,
        accessToken: String,
        userId: UUID,
    ): List<SdkJellyfinLibrary>
}

class SdkJellyfinRepository internal constructor(
    private val probe: JellyfinServerProbe,
    private val authenticator: JellyfinUserAuthenticator,
    private val libraryProvider: JellyfinLibraryProvider,
) : JellyfinRepository {
    constructor(jellyfin: Jellyfin) : this(
        probe = createProbe(jellyfin),
        authenticator = createAuthenticator(jellyfin),
        libraryProvider = createLibraryProvider(jellyfin),
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

    override suspend fun fetchLibraries(
        serverUrl: String,
        session: JellyfinSession,
    ): JellyfinLibrariesResult {
        if (
            serverUrl.isBlank() ||
            session.accessToken.isBlank() ||
            session.userId.isBlank() ||
            session.serverId.isBlank()
        ) {
            return JellyfinLibrariesResult.InvalidSession
        }
        val userId =
            try {
                UUID.fromString(session.userId)
            } catch (_: IllegalArgumentException) {
                return JellyfinLibrariesResult.InvalidSession
            }

        return try {
            val libraries =
                libraryProvider
                    .getUserViews(serverUrl, session.accessToken, userId)
                    .map { library ->
                        JellyfinLibrary(
                            id = library.id,
                            name = library.name,
                            collectionType = library.collectionType,
                        )
                    }
            if (libraries.any { library -> library.id.isBlank() || library.name.isBlank() }) {
                JellyfinLibrariesResult.InvalidResponse
            } else {
                JellyfinLibrariesResult.Success(libraries)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) {
                JellyfinLibrariesResult.InvalidSession
            } else {
                JellyfinLibrariesResult.ServerRejected
            }
        } catch (_: TimeoutException) {
            JellyfinLibrariesResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinLibrariesResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinLibrariesResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinLibrariesResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinLibrariesResult.UnexpectedFailure
        }
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

        fun createLibraryProvider(jellyfin: Jellyfin): JellyfinLibraryProvider =
            JellyfinLibraryProvider { serverUrl, accessToken, userId ->
                jellyfin
                    .createApi(
                        baseUrl = serverUrl,
                        accessToken = accessToken,
                        httpClientOptions = HTTP_CLIENT_OPTIONS,
                    ).userViewApi
                    .getUserViews(userId = userId)
                    .content
                    .items
                    .map { item ->
                        SdkJellyfinLibrary(
                            id = item.id.toString(),
                            name = item.name.orEmpty(),
                            collectionType = item.collectionType?.name,
                        )
                    }
            }
    }
}
