package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.util.concurrent.CancellationException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinRepository
import io.github.justarandombaddev.seerrfintv.playback.PlaybackSourceCandidate
import io.github.justarandombaddev.seerrfintv.playback.PlaybackSourceSelector
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidContentException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.exception.SecureConnectionException
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.authenticateUserByName
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.deviceprofile.buildDeviceProfile

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

data class SdkPlayableItem(
    val id: String,
    val name: String,
    val type: String,
) {
    override fun toString(): String = "SdkPlayableItem(<redacted>)"
}

data class SdkPlayableItemPage(
    val items: List<SdkPlayableItem>,
    val totalRecordCount: Int?,
) {
    override fun toString(): String = "SdkPlayableItemPage(<redacted>)"
}

data class SdkPlayableItemsRequest(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
    val libraryId: UUID,
    val startIndex: Int,
    val limit: Int,
    val recursive: Boolean,
) {
    override fun toString(): String = "SdkPlayableItemsRequest(<redacted>)"
}

fun interface JellyfinPlayableItemProvider {
    suspend fun getPage(request: SdkPlayableItemsRequest): SdkPlayableItemPage
}

data class SdkPlaybackInfoRequest(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
    val item: SdkPlayableItem,
) {
    override fun toString(): String = "SdkPlaybackInfoRequest(<redacted>)"
}

data class SdkPlaybackInfo(
    val playSessionId: String?,
    val errorCode: String?,
    val sources: List<PlaybackSourceCandidate>,
) {
    override fun toString(): String = "SdkPlaybackInfo(<redacted>)"
}

fun interface JellyfinPlaybackInfoProvider {
    suspend fun getPlaybackInfo(request: SdkPlaybackInfoRequest): SdkPlaybackInfo
}

class SdkJellyfinRepository internal constructor(
    private val probe: JellyfinServerProbe,
    private val authenticator: JellyfinUserAuthenticator,
    private val libraryProvider: JellyfinLibraryProvider,
    private val playableItemProvider: JellyfinPlayableItemProvider,
    private val playbackInfoProvider: JellyfinPlaybackInfoProvider,
    private val playbackPageSize: Int = PLAYBACK_PAGE_SIZE,
) : JellyfinRepository {
    constructor(jellyfin: Jellyfin) : this(
        probe = createProbe(jellyfin),
        authenticator = createAuthenticator(jellyfin),
        libraryProvider = createLibraryProvider(jellyfin),
        playableItemProvider = createPlayableItemProvider(jellyfin),
        playbackInfoProvider = createPlaybackInfoProvider(jellyfin),
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

    override suspend fun resolvePlaybackSource(
        serverUrl: String,
        session: JellyfinSession,
        libraryId: String,
    ): JellyfinPlaybackResolutionResult {
        if (
            serverUrl.isBlank() ||
            session.accessToken.isBlank() ||
            session.userId.isBlank() ||
            session.serverId.isBlank()
        ) {
            return JellyfinPlaybackResolutionResult.InvalidSession
        }
        val userId = session.userId.toUuidOrNull() ?: return JellyfinPlaybackResolutionResult.InvalidSession
        val parsedLibraryId = libraryId.toUuidOrNull() ?: return JellyfinPlaybackResolutionResult.InvalidResponse

        return try {
            resolvePlaybackSource(
                serverUrl = serverUrl,
                session = session,
                userId = userId,
                libraryId = parsedLibraryId,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) {
                JellyfinPlaybackResolutionResult.InvalidSession
            } else {
                JellyfinPlaybackResolutionResult.ServerRejected
            }
        } catch (_: TimeoutException) {
            JellyfinPlaybackResolutionResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinPlaybackResolutionResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinPlaybackResolutionResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinPlaybackResolutionResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinPlaybackResolutionResult.UnexpectedFailure
        }
    }

    private suspend fun resolvePlaybackSource(
        serverUrl: String,
        session: JellyfinSession,
        userId: UUID,
        libraryId: UUID,
    ): JellyfinPlaybackResolutionResult {
        var startIndex = 0
        while (true) {
            val page =
                playableItemProvider.getPage(
                    SdkPlayableItemsRequest(
                        serverUrl = serverUrl,
                        accessToken = session.accessToken,
                        userId = userId,
                        libraryId = libraryId,
                        startIndex = startIndex,
                        limit = playbackPageSize,
                        recursive = true,
                    ),
                )
            if (page.items.isEmpty()) return JellyfinPlaybackResolutionResult.Empty

            page.items.forEach { sdkItem ->
                if (
                    sdkItem.id.toUuidOrNull() == null ||
                    sdkItem.name.isBlank() ||
                    sdkItem.type.isBlank()
                ) {
                    return JellyfinPlaybackResolutionResult.InvalidResponse
                }
                val playbackInfo =
                    playbackInfoProvider.getPlaybackInfo(
                        SdkPlaybackInfoRequest(
                            serverUrl = serverUrl,
                            accessToken = session.accessToken,
                            userId = userId,
                            item = sdkItem,
                        ),
                    )
                if (playbackInfo.errorCode == null) {
                    val source =
                        PlaybackSourceSelector.select(
                            item =
                                JellyfinPlayableItem(
                                    id = sdkItem.id,
                                    name = sdkItem.name,
                                    type = sdkItem.type,
                                ),
                            playSessionId = playbackInfo.playSessionId,
                            sources = playbackInfo.sources,
                        )
                    if (source != null) return JellyfinPlaybackResolutionResult.Success(source)
                }
            }

            val nextIndex = startIndex + page.items.size
            val hasMore =
                page.totalRecordCount?.let { nextIndex < it }
                    ?: (page.items.size == playbackPageSize)
            if (!hasMore) return JellyfinPlaybackResolutionResult.Empty
            startIndex = nextIndex
        }
    }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
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
        const val PLAYBACK_PAGE_SIZE = 50

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

        fun createPlayableItemProvider(jellyfin: Jellyfin): JellyfinPlayableItemProvider =
            JellyfinPlayableItemProvider { request ->
                val response =
                    jellyfin
                        .createApi(
                            baseUrl = request.serverUrl,
                            accessToken = request.accessToken,
                            httpClientOptions = HTTP_CLIENT_OPTIONS,
                        ).libraryApi
                        .getItems(
                            request =
                                GetItemsRequest(
                                    userId = request.userId,
                                    startIndex = request.startIndex,
                                    limit = request.limit,
                                    recursive = request.recursive,
                                    sortOrder = listOf(SortOrder.ASCENDING),
                                    parentId = request.libraryId,
                                    includeItemTypes = PLAYABLE_ITEM_TYPES,
                                    filters = listOf(ItemFilter.IS_NOT_FOLDER),
                                    isMissing = false,
                                    sortBy = listOf(ItemSortBy.SORT_NAME),
                                    enableTotalRecordCount = true,
                                    enableImages = false,
                                ),
                        ).content
                SdkPlayableItemPage(
                    items =
                        response.items.map { item ->
                            SdkPlayableItem(
                                id = item.id.toString(),
                                name = item.name.orEmpty(),
                                type = item.type.name,
                            )
                        },
                    totalRecordCount = response.totalRecordCount,
                )
            }

        fun createPlaybackInfoProvider(jellyfin: Jellyfin): JellyfinPlaybackInfoProvider =
            JellyfinPlaybackInfoProvider { request ->
                val api =
                    jellyfin.createApi(
                        baseUrl = request.serverUrl,
                        accessToken = request.accessToken,
                        httpClientOptions = HTTP_CLIENT_OPTIONS,
                    )
                val itemId = UUID.fromString(request.item.id)
                val response =
                    api.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId = itemId,
                            data =
                                PlaybackInfoDto(
                                    userId = request.userId,
                                    deviceProfile = PLAYBACK_DEVICE_PROFILE,
                                    enableDirectPlay = true,
                                    enableDirectStream = true,
                                    enableTranscoding = true,
                                    allowVideoStreamCopy = true,
                                    allowAudioStreamCopy = true,
                                ),
                        ).content
                SdkPlaybackInfo(
                    playSessionId = response.playSessionId,
                    errorCode = response.errorCode?.name,
                    sources =
                        response.mediaSources.map { source ->
                            val directPlayUrl =
                                when {
                                    !source.supportsDirectPlay -> null
                                    source.isRemote && !source.path.isNullOrBlank() -> source.path
                                    else ->
                                        api.videoApi.getVideoStreamUrl(
                                            itemId = itemId,
                                            container = source.container,
                                            static = true,
                                            mediaSourceId = source.id,
                                            tag = source.eTag,
                                            liveStreamId = source.liveStreamId,
                                        )
                                }
                            PlaybackSourceCandidate(
                                mediaSourceId = source.id.orEmpty(),
                                container = source.container,
                                transcodingContainer = source.transcodingContainer,
                                supportsDirectPlay = source.supportsDirectPlay,
                                supportsDirectStream = source.supportsDirectStream,
                                supportsTranscoding = source.supportsTranscoding,
                                directPlayUrl = directPlayUrl,
                                transcodingUrl =
                                    source.transcodingUrl
                                        ?.takeIf(String::isNotBlank)
                                        ?.let { api.createUrl(it, ignorePathParameters = true) },
                                requiredHttpHeaders =
                                    source.requiredHttpHeaders
                                        .orEmpty()
                                        .mapNotNull { (name, value) -> value?.let { name to it } }
                                        .toMap(),
                            )
                        },
                )
            }

        val PLAYABLE_ITEM_TYPES =
            listOf(
                BaseItemKind.MOVIE,
                BaseItemKind.EPISODE,
                BaseItemKind.VIDEO,
            )

        val PLAYBACK_DEVICE_PROFILE =
            buildDeviceProfile {
                name = "Seerrfin TV"
                directPlayProfile {
                    type = DlnaProfileType.VIDEO
                    container("mp4", "m4v")
                    videoCodec("h264")
                    audioCodec("aac")
                }
                transcodingProfile {
                    type = DlnaProfileType.VIDEO
                    context = EncodingContext.STREAMING
                    protocol = MediaStreamProtocol.HLS
                    container = "ts"
                    videoCodec("h264")
                    audioCodec("aac")
                }
            }
    }
}
