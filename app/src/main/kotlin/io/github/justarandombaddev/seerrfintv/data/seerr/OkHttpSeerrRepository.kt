package io.github.justarandombaddev.seerrfintv.data.seerr

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrPermissions
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
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrApiResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrApiRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrAuthenticationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectChallenge
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectCheckResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrQuickConnectInitiationResult
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrRepository
import io.github.justarandombaddev.seerrfintv.domain.repository.SeerrSessionValidationResult
import java.io.IOException
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class OkHttpSeerrRepository(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) : SeerrRepository, SeerrApiRepository {
    override suspend fun initiateQuickConnect(serverUrl: String): SeerrQuickConnectInitiationResult =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(
                    Request.Builder()
                        .url(endpoint(serverUrl, "auth/jellyfin/quickconnect/initiate"))
                        .post(EMPTY_BODY)
                        .build(),
                ).execute().use { response ->
                    when (response.code) {
                        200 -> {
                            val dto = response.decode<QuickConnectInitiationDto>()
                            if (dto.code.isBlank() || dto.secret.isBlank()) {
                                SeerrQuickConnectInitiationResult.InvalidResponse
                            } else {
                                SeerrQuickConnectInitiationResult.Ready(
                                    SeerrQuickConnectChallenge(dto.code, dto.secret),
                                )
                            }
                        }

                        403 -> SeerrQuickConnectInitiationResult.Unavailable
                        else -> SeerrQuickConnectInitiationResult.ServerRejected
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SSLException) {
                SeerrQuickConnectInitiationResult.SecureConnectionFailed
            } catch (_: IOException) {
                SeerrQuickConnectInitiationResult.Unreachable
            } catch (_: SerializationException) {
                SeerrQuickConnectInitiationResult.InvalidResponse
            } catch (_: IllegalArgumentException) {
                SeerrQuickConnectInitiationResult.InvalidResponse
            }
        }

    override suspend fun checkQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrQuickConnectCheckResult =
        withContext(Dispatchers.IO) {
            try {
                val url =
                    endpoint(serverUrl, "auth/jellyfin/quickconnect/check")
                        .newBuilder()
                        .addQueryParameter("secret", secret)
                        .build()
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    when (response.code) {
                        200 ->
                            if (response.decode<QuickConnectCheckDto>().authenticated) {
                                SeerrQuickConnectCheckResult.Authorized
                            } else {
                                SeerrQuickConnectCheckResult.Pending
                            }

                        403 -> SeerrQuickConnectCheckResult.Unavailable
                        404 -> SeerrQuickConnectCheckResult.Expired
                        else -> SeerrQuickConnectCheckResult.ServerRejected
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SSLException) {
                SeerrQuickConnectCheckResult.SecureConnectionFailed
            } catch (_: IOException) {
                SeerrQuickConnectCheckResult.Unreachable
            } catch (_: SerializationException) {
                SeerrQuickConnectCheckResult.InvalidResponse
            } catch (_: IllegalArgumentException) {
                SeerrQuickConnectCheckResult.InvalidResponse
            }
        }

    override suspend fun authenticateQuickConnect(
        serverUrl: String,
        secret: String,
    ): SeerrAuthenticationResult =
        withContext(Dispatchers.IO) {
            try {
                val url = endpoint(serverUrl, "auth/jellyfin/quickconnect/authenticate")
                val requestBody =
                    json.encodeToString(QuickConnectSecretDto(secret))
                        .toRequestBody(JSON_MEDIA_TYPE)
                client.newCall(Request.Builder().url(url).post(requestBody).build()).execute().use { response ->
                    when (response.code) {
                        200 -> authenticateResponse(serverUrl, url, response)
                        401, 403 -> SeerrAuthenticationResult.AccessDenied
                        else -> SeerrAuthenticationResult.ServerRejected
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SSLException) {
                SeerrAuthenticationResult.SecureConnectionFailed
            } catch (_: IOException) {
                SeerrAuthenticationResult.Unreachable
            } catch (_: SerializationException) {
                SeerrAuthenticationResult.InvalidResponse
            } catch (_: IllegalArgumentException) {
                SeerrAuthenticationResult.InvalidResponse
            }
        }

    override suspend fun validateSession(session: SeerrSession): SeerrSessionValidationResult =
        withContext(Dispatchers.IO) {
            if (session.isExpired(nowEpochMillis())) {
                return@withContext SeerrSessionValidationResult.Expired
            }
            try {
                validateSessionRequest(session)
            } catch (error: CancellationException) {
                throw error
            } catch (_: SSLException) {
                SeerrSessionValidationResult.SecureConnectionFailed
            } catch (_: IOException) {
                SeerrSessionValidationResult.Unreachable
            } catch (_: SerializationException) {
                SeerrSessionValidationResult.InvalidResponse
            } catch (_: IllegalArgumentException) {
                SeerrSessionValidationResult.InvalidResponse
            }
        }

    override suspend fun discoverMovies(
        session: SeerrSession,
        page: Int,
        language: String?,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>> =
        pagedMediaRequest(session, "discover/movies", page, language, SeerrMediaType.MOVIE)

    override suspend fun discoverSeries(
        session: SeerrSession,
        page: Int,
        language: String?,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>> =
        pagedMediaRequest(session, "discover/tv", page, language, SeerrMediaType.TV)

    override suspend fun discoverTrending(
        session: SeerrSession,
        page: Int,
        language: String?,
        mediaType: SeerrMediaType,
        timeWindow: SeerrTimeWindow,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>> {
        require(mediaType != SeerrMediaType.PERSON)
        val url =
            pagedUrl(session.serverUrl, "discover/trending", page, language)
                .newBuilder()
                .addQueryParameter("mediaType", mediaType.apiValue)
                .addQueryParameter("timeWindow", timeWindow.apiValue)
                .build()
        return executeApi(session, Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString<SeerrPagedDto>(body).toDomain(json)
        }
    }

    override suspend fun search(
        session: SeerrSession,
        query: String,
        page: Int,
        language: String?,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>> {
        require(query.isNotBlank())
        require(page > 0)
        val orderedUrl =
            endpoint(session.serverUrl, "search")
                .newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("page", page.toString())
                .apply { language?.let { addQueryParameter("language", it) } }
                .build()
        return executeApi(session, Request.Builder().url(orderedUrl).get().build()) { body ->
            json.decodeFromString<SeerrPagedDto>(body).toDomain(json)
        }
    }

    override suspend fun movieDetails(
        session: SeerrSession,
        tmdbId: Long,
        language: String?,
    ): SeerrApiResult<SeerrMovieDetails> {
        val url = detailsUrl(session.serverUrl, "movie", tmdbId, language)
        return executeApi(session, Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString<SeerrMovieDetailsDto>(body).toDomain()
        }
    }

    override suspend fun tvDetails(
        session: SeerrSession,
        tmdbId: Long,
        language: String?,
    ): SeerrApiResult<SeerrTvDetails> {
        val url = detailsUrl(session.serverUrl, "tv", tmdbId, language)
        return executeApi(session, Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString<SeerrTvDetailsDto>(body).toDomain()
        }
    }

    override suspend fun requests(
        session: SeerrSession,
        take: Int?,
        skip: Int?,
        filter: SeerrRequestFilter?,
        sort: SeerrRequestSort,
        sortDirection: SeerrSortDirection,
        requestedBy: Long?,
        mediaType: SeerrMediaType,
    ): SeerrApiResult<SeerrRequestPage> {
        require(mediaType != SeerrMediaType.PERSON)
        val url =
            endpoint(session.serverUrl, "request")
                .newBuilder()
                .apply {
                    take?.let { addQueryParameter("take", it.toString()) }
                    skip?.let { addQueryParameter("skip", it.toString()) }
                    filter?.let { addQueryParameter("filter", it.apiValue) }
                    addQueryParameter("sort", sort.apiValue)
                    addQueryParameter("sortDirection", sortDirection.apiValue)
                    requestedBy?.let { addQueryParameter("requestedBy", it.toString()) }
                    addQueryParameter("mediaType", mediaType.apiValue)
                }.build()
        return executeApi(session, Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString<SeerrRequestPageDto>(body).toDomain()
        }
    }

    override suspend fun createMovieRequest(
        session: SeerrSession,
        tmdbId: Long,
        is4k: Boolean,
    ): SeerrApiResult<SeerrMediaRequest> =
        createRequest(session, SeerrMediaType.MOVIE, tmdbId, seasons = null, is4k)

    override suspend fun createTvRequest(
        session: SeerrSession,
        tmdbId: Long,
        seasons: SeerrTvSeasonSelection,
        is4k: Boolean,
    ): SeerrApiResult<SeerrMediaRequest> {
        val seasonsJson =
            when (seasons) {
                SeerrTvSeasonSelection.All -> JsonPrimitive("all")
                is SeerrTvSeasonSelection.Selected ->
                    JsonArray(seasons.seasonNumbers.map(::JsonPrimitive))
            }
        return createRequest(session, SeerrMediaType.TV, tmdbId, seasonsJson, is4k)
    }

    private suspend fun pagedMediaRequest(
        session: SeerrSession,
        path: String,
        page: Int,
        language: String?,
        defaultMediaType: SeerrMediaType,
    ): SeerrApiResult<SeerrPagedResult<SeerrMediaSummary>> {
        val url = pagedUrl(session.serverUrl, path, page, language)
        return executeApi(session, Request.Builder().url(url).get().build()) { body ->
            json.decodeFromString<SeerrPagedDto>(body).toDomain(json, defaultMediaType)
        }
    }

    private suspend fun createRequest(
        session: SeerrSession,
        mediaType: SeerrMediaType,
        tmdbId: Long,
        seasons: kotlinx.serialization.json.JsonElement?,
        is4k: Boolean,
    ): SeerrApiResult<SeerrMediaRequest> {
        require(tmdbId > 0)
        val body =
            json.encodeToString(
                SeerrCreateRequestDto(
                    mediaType = mediaType.apiValue,
                    mediaId = tmdbId,
                    seasons = seasons,
                    is4k = is4k,
                ),
            ).toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder().url(endpoint(session.serverUrl, "request")).post(body).build()
        return executeApi(session, request) { responseBody ->
            json.decodeFromString<SeerrMediaRequestDto>(responseBody).toDomain()
        }
    }

    private suspend fun <T> executeApi(
        session: SeerrSession,
        request: Request,
        decode: (String) -> T,
    ): SeerrApiResult<T> =
        withContext(Dispatchers.IO) {
            if (session.isExpired(nowEpochMillis())) {
                return@withContext SeerrApiResult.SessionExpired
            }
            if (
                session.cookieName != SESSION_COOKIE_NAME ||
                (session.secure && !request.url.isHttps)
            ) {
                return@withContext SeerrApiResult.InvalidResponse
            }
            try {
                val authenticatedRequest =
                    request.newBuilder()
                        .header("Cookie", "${session.cookieName}=${session.cookieValue}")
                        .build()
                client.newCall(authenticatedRequest).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> SeerrApiResult.Success(decode(response.body.string()))
                        401 -> SeerrApiResult.SessionExpired
                        403 -> SeerrApiResult.PermissionDenied
                        404 -> SeerrApiResult.NotFound
                        else -> SeerrApiResult.ServerRejected
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: SSLException) {
                SeerrApiResult.SecureConnectionFailed
            } catch (_: IOException) {
                SeerrApiResult.Unreachable
            } catch (_: SerializationException) {
                SeerrApiResult.InvalidResponse
            } catch (_: IllegalArgumentException) {
                SeerrApiResult.InvalidResponse
            }
        }

    private fun pagedUrl(
        serverUrl: String,
        path: String,
        page: Int,
        language: String?,
    ): HttpUrl {
        require(page > 0)
        return endpoint(serverUrl, path)
            .newBuilder()
            .addQueryParameter("page", page.toString())
            .apply { language?.let { addQueryParameter("language", it) } }
            .build()
    }

    private fun detailsUrl(
        serverUrl: String,
        mediaPath: String,
        tmdbId: Long,
        language: String?,
    ): HttpUrl {
        require(tmdbId > 0)
        return endpoint(serverUrl, "$mediaPath/$tmdbId")
            .newBuilder()
            .apply { language?.let { addQueryParameter("language", it) } }
            .build()
    }

    private fun authenticateResponse(
        serverUrl: String,
        responseUrl: HttpUrl,
        response: Response,
    ): SeerrAuthenticationResult {
        val cookie =
            response.headers("Set-Cookie")
                .mapNotNull { header -> Cookie.parse(responseUrl, header) }
                .firstOrNull { cookie -> cookie.name == SESSION_COOKIE_NAME }
                ?: return SeerrAuthenticationResult.InvalidResponse
        if (cookie.expiresAt <= nowEpochMillis()) {
            return SeerrAuthenticationResult.InvalidResponse
        }
        val session =
            SeerrSession(
                serverUrl = serverUrl,
                cookieName = cookie.name,
                cookieValue = cookie.value,
                expiresAtEpochMillis = cookie.expiresAt,
                secure = cookie.secure,
            )
        return when (val validation = validateSessionRequest(session)) {
            is SeerrSessionValidationResult.Authenticated ->
                SeerrAuthenticationResult.Authenticated(session, validation.user)
            SeerrSessionValidationResult.Expired -> SeerrAuthenticationResult.AccessDenied
            SeerrSessionValidationResult.Unreachable -> SeerrAuthenticationResult.Unreachable
            SeerrSessionValidationResult.SecureConnectionFailed ->
                SeerrAuthenticationResult.SecureConnectionFailed
            SeerrSessionValidationResult.ServerRejected -> SeerrAuthenticationResult.ServerRejected
            SeerrSessionValidationResult.InvalidResponse -> SeerrAuthenticationResult.InvalidResponse
        }
    }

    private fun validateSessionRequest(session: SeerrSession): SeerrSessionValidationResult {
        val url = endpoint(session.serverUrl, "auth/me")
        if (session.cookieName != SESSION_COOKIE_NAME || (session.secure && !url.isHttps)) {
            return SeerrSessionValidationResult.InvalidResponse
        }
        val request =
            Request.Builder()
                .url(url)
                .header("Cookie", "${session.cookieName}=${session.cookieValue}")
                .get()
                .build()
        client.newCall(request).execute().use { response ->
            return when (response.code) {
                200 -> {
                    val dto = response.decode<SeerrUserDto>()
                    val permissions = dto.permissions ?: return SeerrSessionValidationResult.InvalidResponse
                    val displayName = dto.username?.takeIf(String::isNotBlank) ?: dto.email
                    if (dto.id <= 0L || displayName.isBlank()) {
                        SeerrSessionValidationResult.InvalidResponse
                    } else {
                        SeerrSessionValidationResult.Authenticated(
                            SeerrUser(dto.id, displayName, SeerrPermissions(permissions)),
                        )
                    }
                }

                401, 403 -> SeerrSessionValidationResult.Expired
                else -> SeerrSessionValidationResult.ServerRejected
            }
        }
    }

    private inline fun <reified T> Response.decode(): T {
        val content = body.string()
        return json.decodeFromString(content)
    }

    private fun endpoint(
        serverUrl: String,
        relativePath: String,
    ): HttpUrl =
        requireNotNull(serverUrl.toHttpUrl().resolve("api/v1/$relativePath"))

    private companion object {
        const val SESSION_COOKIE_NAME = "connect.sid"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_BODY = ByteArray(0).toRequestBody(null)
    }
}

@Serializable
private data class QuickConnectInitiationDto(
    val code: String,
    val secret: String,
)

@Serializable
private data class QuickConnectCheckDto(
    val authenticated: Boolean,
)

@Serializable
private data class QuickConnectSecretDto(
    val secret: String,
)

@Serializable
private data class SeerrUserDto(
    val id: Long,
    val email: String,
    val username: String? = null,
    val permissions: Long? = null,
)
