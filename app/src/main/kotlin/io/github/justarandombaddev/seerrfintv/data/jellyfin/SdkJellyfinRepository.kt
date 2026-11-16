package io.github.justarandombaddev.seerrfintv.data.jellyfin

import java.util.concurrent.CancellationException
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinHomeRow
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReportKind
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSession
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSeason
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinConnectionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinLibrariesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinHomeResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinEpisodesResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinSeriesDetailsResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackResolutionResult
import io.github.justarandombaddev.seerrfintv.domain.repository.JellyfinPlaybackReportResult
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
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PublicSystemInfo
import org.jellyfin.sdk.model.api.RepeatMode
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
    val playbackPositionTicks: Long = 0,
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

data class SdkHomeItemsRequest(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
    val libraryId: UUID,
    val itemTypes: List<BaseItemKind>,
    val limit: Int,
) {
    override fun toString(): String = "SdkHomeItemsRequest(<redacted>)"
}

data class SdkCatalogItem(
    val id: String,
    val name: String,
    val type: String,
    val productionYear: Int?,
    val playbackPositionTicks: Long,
) {
    override fun toString(): String = "SdkCatalogItem(<redacted>)"
}

fun interface JellyfinHomeItemProvider {
    suspend fun getLatestItems(request: SdkHomeItemsRequest): List<SdkCatalogItem>
}

data class SdkSeasonItem(
    val id: String,
    val name: String,
    val indexNumber: Int?,
)

data class SdkEpisodeItem(
    val id: String,
    val name: String,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val playbackPositionTicks: Long,
)

data class SdkSeriesRequest(
    val serverUrl: String,
    val accessToken: String,
    val userId: UUID,
    val seriesId: UUID,
    val seasonId: UUID?,
) {
    override fun toString(): String = "SdkSeriesRequest(<redacted>)"

    companion object {
        fun seasons(
            serverUrl: String,
            session: JellyfinSession,
            seriesId: String,
        ) = SdkSeriesRequest(
            serverUrl = serverUrl,
            accessToken = session.accessToken,
            userId = UUID.fromString(session.userId),
            seriesId = UUID.fromString(seriesId),
            seasonId = null,
        )

        fun episodes(
            serverUrl: String,
            session: JellyfinSession,
            seriesId: String,
            seasonId: String,
        ) = SdkSeriesRequest(
            serverUrl = serverUrl,
            accessToken = session.accessToken,
            userId = UUID.fromString(session.userId),
            seriesId = UUID.fromString(seriesId),
            seasonId = UUID.fromString(seasonId),
        )
    }
}

interface JellyfinSeriesProvider {
    suspend fun getSeasons(request: SdkSeriesRequest): List<SdkSeasonItem>

    suspend fun getEpisodes(request: SdkSeriesRequest): List<SdkEpisodeItem>
}

private object EmptyJellyfinSeriesProvider : JellyfinSeriesProvider {
    override suspend fun getSeasons(request: SdkSeriesRequest): List<SdkSeasonItem> = emptyList()

    override suspend fun getEpisodes(request: SdkSeriesRequest): List<SdkEpisodeItem> = emptyList()
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

data class SdkPlaybackReportRequest(
    val serverUrl: String,
    val accessToken: String,
    val report: JellyfinPlaybackReport,
) {
    override fun toString(): String = "SdkPlaybackReportRequest(<redacted>)"
}

fun interface JellyfinPlaybackReportProvider {
    suspend fun report(request: SdkPlaybackReportRequest)
}

class SdkJellyfinRepository internal constructor(
    private val probe: JellyfinServerProbe,
    private val authenticator: JellyfinUserAuthenticator,
    private val libraryProvider: JellyfinLibraryProvider,
    private val playableItemProvider: JellyfinPlayableItemProvider,
    private val playbackInfoProvider: JellyfinPlaybackInfoProvider,
    private val homeItemProvider: JellyfinHomeItemProvider = JellyfinHomeItemProvider { emptyList() },
    private val seriesProvider: JellyfinSeriesProvider = EmptyJellyfinSeriesProvider,
    private val playbackReportProvider: JellyfinPlaybackReportProvider = JellyfinPlaybackReportProvider {},
    private val playbackPageSize: Int = PLAYBACK_PAGE_SIZE,
) : JellyfinRepository {
    constructor(jellyfin: Jellyfin) : this(
        probe = createProbe(jellyfin),
        authenticator = createAuthenticator(jellyfin),
        libraryProvider = createLibraryProvider(jellyfin),
        playableItemProvider = createPlayableItemProvider(jellyfin),
        playbackInfoProvider = createPlaybackInfoProvider(jellyfin),
        homeItemProvider = createHomeItemProvider(jellyfin),
        seriesProvider = createSeriesProvider(jellyfin),
        playbackReportProvider = createPlaybackReportProvider(jellyfin),
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

    override suspend fun fetchHomeRows(
        serverUrl: String,
        session: JellyfinSession,
        libraries: List<JellyfinLibrary>,
    ): JellyfinHomeResult {
        if (
            serverUrl.isBlank() ||
            session.accessToken.isBlank() ||
            session.userId.isBlank() ||
            session.serverId.isBlank()
        ) {
            return JellyfinHomeResult.InvalidSession
        }
        val userId = session.userId.toUuidOrNull() ?: return JellyfinHomeResult.InvalidSession

        return try {
            val rows =
                libraries.mapNotNull { library ->
                    val itemTypes = library.catalogItemTypes() ?: return@mapNotNull null
                    val libraryId = library.id.toUuidOrNull() ?: return JellyfinHomeResult.InvalidResponse
                    val items =
                        homeItemProvider
                            .getLatestItems(
                                SdkHomeItemsRequest(
                                    serverUrl = serverUrl,
                                    accessToken = session.accessToken,
                                    userId = userId,
                                    libraryId = libraryId,
                                    itemTypes = itemTypes,
                                    limit = HOME_ROW_ITEM_LIMIT,
                                ),
                            ).map { item -> item.toCatalogItem() ?: return JellyfinHomeResult.InvalidResponse }
                    if (items.isEmpty()) return@mapNotNull null
                    JellyfinHomeRow(library = library, items = items)
                }
            JellyfinHomeResult.Success(rows)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) JellyfinHomeResult.InvalidSession else JellyfinHomeResult.ServerRejected
        } catch (_: TimeoutException) {
            JellyfinHomeResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinHomeResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinHomeResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinHomeResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinHomeResult.UnexpectedFailure
        }
    }

    override suspend fun fetchSeriesDetails(
        serverUrl: String,
        session: JellyfinSession,
        series: JellyfinCatalogItem,
    ): JellyfinSeriesDetailsResult {
        if (!session.isUsable(serverUrl)) return JellyfinSeriesDetailsResult.InvalidSession
        if (series.id.toUuidOrNull() == null) return JellyfinSeriesDetailsResult.InvalidResponse
        if (series.type != JellyfinCatalogItemType.Series || series.name.isBlank()) {
            return JellyfinSeriesDetailsResult.InvalidResponse
        }
        return try {
            val seasonsRequest = SdkSeriesRequest.seasons(serverUrl, session, series.id)
            val seasons =
                seriesProvider.getSeasons(seasonsRequest).map { item ->
                    item.toSeason() ?: return JellyfinSeriesDetailsResult.InvalidResponse
                }
            val selectedSeasonId = seasons.firstOrNull()?.id
            val episodes =
                selectedSeasonId?.let { seasonId ->
                    seriesProvider
                        .getEpisodes(SdkSeriesRequest.episodes(serverUrl, session, series.id, seasonId))
                        .map { item -> item.toEpisode() ?: return JellyfinSeriesDetailsResult.InvalidResponse }
                }.orEmpty()
            JellyfinSeriesDetailsResult.Success(seasons, selectedSeasonId, episodes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) JellyfinSeriesDetailsResult.InvalidSession
            else JellyfinSeriesDetailsResult.ServerRejected
        } catch (_: TimeoutException) {
            JellyfinSeriesDetailsResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinSeriesDetailsResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinSeriesDetailsResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinSeriesDetailsResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinSeriesDetailsResult.UnexpectedFailure
        }
    }

    override suspend fun fetchSeasonEpisodes(
        serverUrl: String,
        session: JellyfinSession,
        seriesId: String,
        seasonId: String,
    ): JellyfinEpisodesResult {
        if (!session.isUsable(serverUrl)) return JellyfinEpisodesResult.InvalidSession
        if (seriesId.toUuidOrNull() == null || seasonId.toUuidOrNull() == null) {
            return JellyfinEpisodesResult.InvalidResponse
        }
        return try {
            val episodes =
                seriesProvider
                    .getEpisodes(SdkSeriesRequest.episodes(serverUrl, session, seriesId, seasonId))
                    .map { item -> item.toEpisode() ?: return JellyfinEpisodesResult.InvalidResponse }
            JellyfinEpisodesResult.Success(episodes)
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) JellyfinEpisodesResult.InvalidSession
            else JellyfinEpisodesResult.ServerRejected
        } catch (_: TimeoutException) {
            JellyfinEpisodesResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinEpisodesResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinEpisodesResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinEpisodesResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinEpisodesResult.UnexpectedFailure
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

    override suspend fun resolvePlaybackItem(
        serverUrl: String,
        session: JellyfinSession,
        item: JellyfinCatalogItem,
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
        if (item.id.toUuidOrNull() == null || item.name.isBlank() || item.playbackPositionTicks < 0) {
            return JellyfinPlaybackResolutionResult.InvalidResponse
        }
        val sdkType =
            when (item.type) {
                JellyfinCatalogItemType.Movie -> BaseItemKind.MOVIE.name
                JellyfinCatalogItemType.Episode -> BaseItemKind.EPISODE.name
                JellyfinCatalogItemType.Video -> BaseItemKind.VIDEO.name
                JellyfinCatalogItemType.Series -> return JellyfinPlaybackResolutionResult.Empty
            }

        return try {
            resolvePlayableItem(
                serverUrl = serverUrl,
                session = session,
                userId = userId,
                item =
                    SdkPlayableItem(
                        id = item.id,
                        name = item.name,
                        type = sdkType,
                        playbackPositionTicks = item.playbackPositionTicks,
                    ),
            ) ?: JellyfinPlaybackResolutionResult.Empty
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

    override suspend fun reportPlayback(
        serverUrl: String,
        session: JellyfinSession,
        report: JellyfinPlaybackReport,
    ): JellyfinPlaybackReportResult {
        if (
            serverUrl.isBlank() ||
            session.accessToken.isBlank() ||
            session.userId.isBlank() ||
            session.serverId.isBlank()
        ) {
            return JellyfinPlaybackReportResult.InvalidSession
        }
        if (
            session.userId.toUuidOrNull() == null ||
            report.itemId.toUuidOrNull() == null ||
            report.mediaSourceId.isBlank()
        ) {
            return JellyfinPlaybackReportResult.InvalidResponse
        }

        return try {
            playbackReportProvider.report(
                SdkPlaybackReportRequest(
                    serverUrl = serverUrl,
                    accessToken = session.accessToken,
                    report = report,
                ),
            )
            JellyfinPlaybackReportResult.Success
        } catch (error: CancellationException) {
            throw error
        } catch (error: InvalidStatusException) {
            if (error.status == HTTP_UNAUTHORIZED) {
                JellyfinPlaybackReportResult.InvalidSession
            } else {
                JellyfinPlaybackReportResult.ServerRejected
            }
        } catch (_: TimeoutException) {
            JellyfinPlaybackReportResult.Unreachable
        } catch (_: SecureConnectionException) {
            JellyfinPlaybackReportResult.SecureConnectionFailed
        } catch (_: InvalidContentException) {
            JellyfinPlaybackReportResult.InvalidResponse
        } catch (_: ApiClientException) {
            JellyfinPlaybackReportResult.UnexpectedFailure
        } catch (_: Exception) {
            JellyfinPlaybackReportResult.UnexpectedFailure
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
                resolvePlayableItem(serverUrl, session, userId, sdkItem)?.let { return it }
            }

            val nextIndex = startIndex + page.items.size
            val hasMore =
                page.totalRecordCount?.let { nextIndex < it }
                    ?: (page.items.size == playbackPageSize)
            if (!hasMore) return JellyfinPlaybackResolutionResult.Empty
            startIndex = nextIndex
        }
    }

    private suspend fun resolvePlayableItem(
        serverUrl: String,
        session: JellyfinSession,
        userId: UUID,
        item: SdkPlayableItem,
    ): JellyfinPlaybackResolutionResult.Success? {
        val playbackInfo =
            playbackInfoProvider.getPlaybackInfo(
                SdkPlaybackInfoRequest(
                    serverUrl = serverUrl,
                    accessToken = session.accessToken,
                    userId = userId,
                    item = item,
                ),
            )
        if (playbackInfo.errorCode != null) return null
        val source =
            PlaybackSourceSelector.select(
                item =
                    JellyfinPlayableItem(
                        id = item.id,
                        name = item.name,
                        type = item.type,
                        resumePositionMs = item.playbackPositionTicks.coerceAtLeast(0) / TICKS_PER_MILLISECOND,
                    ),
                playSessionId = playbackInfo.playSessionId,
                sources = playbackInfo.sources,
            ) ?: return null
        return JellyfinPlaybackResolutionResult.Success(source)
    }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun JellyfinSession.isUsable(serverUrl: String): Boolean =
        serverUrl.isNotBlank() &&
            accessToken.isNotBlank() &&
            userId.toUuidOrNull() != null &&
            serverId.isNotBlank()

    private fun SdkSeasonItem.toSeason(): JellyfinSeason? {
        if (id.toUuidOrNull() == null || name.isBlank()) return null
        return JellyfinSeason(id = id, name = name, indexNumber = indexNumber)
    }

    private fun SdkEpisodeItem.toEpisode(): JellyfinCatalogItem? {
        if (id.toUuidOrNull() == null || name.isBlank() || playbackPositionTicks < 0) return null
        return JellyfinCatalogItem(
            id = id,
            name = name,
            type = JellyfinCatalogItemType.Episode,
            playbackPositionTicks = playbackPositionTicks,
            indexNumber = episodeNumber,
            parentIndexNumber = seasonNumber,
        )
    }

    private fun JellyfinLibrary.catalogItemTypes(): List<BaseItemKind>? =
        when (collectionType) {
            "MOVIES" -> listOf(BaseItemKind.MOVIE)
            "TVSHOWS" -> listOf(BaseItemKind.SERIES)
            "HOMEVIDEOS" -> listOf(BaseItemKind.VIDEO, BaseItemKind.MOVIE)
            else -> null
        }

    private fun SdkCatalogItem.toCatalogItem(): JellyfinCatalogItem? {
        val mappedType =
            when (type) {
                BaseItemKind.MOVIE.name -> JellyfinCatalogItemType.Movie
                BaseItemKind.SERIES.name -> JellyfinCatalogItemType.Series
                BaseItemKind.VIDEO.name -> JellyfinCatalogItemType.Video
                else -> return null
            }
        if (id.toUuidOrNull() == null || name.isBlank() || playbackPositionTicks < 0) return null
        return JellyfinCatalogItem(
            id = id,
            name = name,
            type = mappedType,
            productionYear = productionYear,
            playbackPositionTicks = playbackPositionTicks,
        )
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
        const val HOME_ROW_ITEM_LIMIT = 20
        const val EPISODE_LIMIT = 200
        const val TICKS_PER_MILLISECOND = 10_000L

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
                                playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0,
                            )
                        },
                    totalRecordCount = response.totalRecordCount,
                )
            }

        fun createHomeItemProvider(jellyfin: Jellyfin): JellyfinHomeItemProvider =
            JellyfinHomeItemProvider { request ->
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
                                limit = request.limit,
                                recursive = true,
                                sortOrder = listOf(SortOrder.DESCENDING),
                                parentId = request.libraryId,
                                includeItemTypes = request.itemTypes,
                                filters = listOf(ItemFilter.IS_NOT_FOLDER),
                                isMissing = false,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                enableUserData = true,
                                enableTotalRecordCount = false,
                                enableImages = false,
                            ),
                    ).content.items
                    .map { item ->
                        SdkCatalogItem(
                            id = item.id.toString(),
                            name = item.name.orEmpty(),
                            type = item.type.name,
                            productionYear = item.productionYear,
                            playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0,
                        )
                    }
            }

        fun createSeriesProvider(jellyfin: Jellyfin): JellyfinSeriesProvider =
            object : JellyfinSeriesProvider {
                override suspend fun getSeasons(request: SdkSeriesRequest): List<SdkSeasonItem> =
                    jellyfin
                        .createApi(
                            baseUrl = request.serverUrl,
                            accessToken = request.accessToken,
                            httpClientOptions = HTTP_CLIENT_OPTIONS,
                        ).showApi
                        .getSeasons(
                            seriesId = request.seriesId,
                            userId = request.userId,
                            enableImages = false,
                            enableUserData = true,
                        ).content.items
                        .map { item ->
                            SdkSeasonItem(
                                id = item.id.toString(),
                                name = item.name.orEmpty(),
                                indexNumber = item.indexNumber,
                            )
                        }

                override suspend fun getEpisodes(request: SdkSeriesRequest): List<SdkEpisodeItem> =
                    jellyfin
                        .createApi(
                            baseUrl = request.serverUrl,
                            accessToken = request.accessToken,
                            httpClientOptions = HTTP_CLIENT_OPTIONS,
                        ).showApi
                        .getEpisodes(
                            seriesId = request.seriesId,
                            userId = request.userId,
                            seasonId = requireNotNull(request.seasonId),
                            startIndex = 0,
                            limit = EPISODE_LIMIT,
                            enableImages = false,
                            enableUserData = true,
                        ).content.items
                        .map { item ->
                            SdkEpisodeItem(
                                id = item.id.toString(),
                                name = item.name.orEmpty(),
                                seasonNumber = item.parentIndexNumber,
                                episodeNumber = item.indexNumber,
                                playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0,
                            )
                        }
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
                            data = request.toPlaybackInfoDto(PLAYBACK_DEVICE_PROFILE),
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

        fun createPlaybackReportProvider(jellyfin: Jellyfin): JellyfinPlaybackReportProvider =
            JellyfinPlaybackReportProvider { request ->
                val sessionApi =
                    jellyfin
                        .createApi(
                            baseUrl = request.serverUrl,
                            accessToken = request.accessToken,
                            httpClientOptions = HTTP_CLIENT_OPTIONS,
                        ).sessionApi
                when (request.report.kind) {
                    JellyfinPlaybackReportKind.Start ->
                        sessionApi.reportPlaybackStart(request.report.toPlaybackStartInfo())

                    JellyfinPlaybackReportKind.Progress ->
                        sessionApi.reportPlaybackProgress(request.report.toPlaybackProgressInfo())

                    JellyfinPlaybackReportKind.Stop ->
                        sessionApi.reportPlaybackStopped(request.report.toPlaybackStopInfo())
                }
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

internal fun SdkPlaybackInfoRequest.toPlaybackInfoDto(deviceProfile: DeviceProfile): PlaybackInfoDto =
    PlaybackInfoDto(
        userId = userId,
        startTimeTicks = item.playbackPositionTicks.coerceAtLeast(0),
        deviceProfile = deviceProfile,
        enableDirectPlay = true,
        enableDirectStream = true,
        enableTranscoding = true,
        allowVideoStreamCopy = true,
        allowAudioStreamCopy = true,
    )

internal fun JellyfinPlaybackReport.toPlaybackStartInfo(): PlaybackStartInfo =
    PlaybackStartInfo(
        canSeek = isSeekable,
        itemId = UUID.fromString(itemId),
        mediaSourceId = mediaSourceId,
        isPaused = isPaused,
        isMuted = false,
        positionTicks = positionMs.toTicks(),
        playMethod = method.toSdkPlayMethod(),
        playSessionId = playSessionId,
        repeatMode = RepeatMode.REPEAT_NONE,
        playbackOrder = PlaybackOrder.DEFAULT,
    )

internal fun JellyfinPlaybackReport.toPlaybackProgressInfo(): PlaybackProgressInfo =
    PlaybackProgressInfo(
        canSeek = isSeekable,
        itemId = UUID.fromString(itemId),
        mediaSourceId = mediaSourceId,
        isPaused = isPaused,
        isMuted = false,
        positionTicks = positionMs.toTicks(),
        playMethod = method.toSdkPlayMethod(),
        playSessionId = playSessionId,
        repeatMode = RepeatMode.REPEAT_NONE,
        playbackOrder = PlaybackOrder.DEFAULT,
    )

internal fun JellyfinPlaybackReport.toPlaybackStopInfo(): PlaybackStopInfo =
    PlaybackStopInfo(
        itemId = UUID.fromString(itemId),
        mediaSourceId = mediaSourceId,
        positionTicks = positionMs.toTicks(),
        playSessionId = playSessionId,
        failed = failed,
    )

private fun Long.toTicks(): Long =
    coerceAtLeast(0)
        .coerceAtMost(Long.MAX_VALUE / 10_000L) * 10_000L

private fun PlaybackMethod.toSdkPlayMethod(): PlayMethod =
    when (this) {
        PlaybackMethod.DIRECT_PLAY -> PlayMethod.DIRECT_PLAY
        PlaybackMethod.DIRECT_STREAM -> PlayMethod.DIRECT_STREAM
        PlaybackMethod.TRANSCODE -> PlayMethod.TRANSCODE
    }
