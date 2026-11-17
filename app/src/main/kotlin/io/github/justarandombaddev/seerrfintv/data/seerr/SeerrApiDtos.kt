package io.github.justarandombaddev.seerrfintv.data.seerr

import io.github.justarandombaddev.seerrfintv.domain.model.SeerrAvailabilityStatus
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrGenre
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaInfo
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaRequest
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaSummary
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMediaType
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrMovieDetails
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrPagedResult
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestPage
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestPageInfo
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrRequestStatus
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrSeason
import io.github.justarandombaddev.seerrfintv.domain.model.SeerrTvDetails
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
internal data class SeerrPagedDto(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<JsonObject>,
)

@Serializable
internal data class SeerrGenreDto(
    val id: Long,
    val name: String,
)

@Serializable
internal data class SeerrEmbeddedRequestDto(
    val id: Long,
    val status: Int,
    val is4k: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class SeerrMediaInfoDto(
    val id: Long,
    val tmdbId: Long,
    val tvdbId: Long? = null,
    val status: Int,
    val requests: List<SeerrEmbeddedRequestDto> = emptyList(),
)

@Serializable
internal data class SeerrMediaSummaryDto(
    val id: Long,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val posterPath: String? = null,
    val profilePath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: SeerrMediaInfoDto? = null,
)

@Serializable
internal data class SeerrMovieDetailsDto(
    val id: Long,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    val mediaInfo: SeerrMediaInfoDto? = null,
)

@Serializable
internal data class SeerrSeasonDto(
    val id: Long,
    val name: String,
    val seasonNumber: Int,
    val episodeCount: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
)

@Serializable
internal data class SeerrTvDetailsDto(
    val id: Long,
    val name: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    val seasons: List<SeerrSeasonDto> = emptyList(),
    val mediaInfo: SeerrMediaInfoDto? = null,
)

@Serializable
internal data class SeerrMediaRequestDto(
    val id: Long,
    val status: Int,
    val media: SeerrMediaInfoDto? = null,
    val is4k: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
internal data class SeerrRequestPageInfoDto(
    val page: Int,
    val pages: Int,
    val results: Int,
)

@Serializable
internal data class SeerrRequestPageDto(
    val pageInfo: SeerrRequestPageInfoDto,
    val results: List<SeerrMediaRequestDto>,
)

@Serializable
internal data class SeerrCreateRequestDto(
    val mediaType: String,
    val mediaId: Long,
    val seasons: JsonElement? = null,
    val is4k: Boolean,
)

internal fun SeerrPagedDto.toDomain(
    json: Json,
    defaultMediaType: SeerrMediaType? = null,
): SeerrPagedResult<SeerrMediaSummary> =
    SeerrPagedResult(
        page = page,
        totalPages = totalPages,
        totalResults = totalResults,
        results = results.map { item ->
            json.decodeFromJsonElement(SeerrMediaSummaryDto.serializer(), item).toDomain(defaultMediaType)
        },
    )

private fun SeerrMediaSummaryDto.toDomain(defaultMediaType: SeerrMediaType?): SeerrMediaSummary {
    val type = mediaType?.toMediaType() ?: defaultMediaType
    requireNotNull(type)
    val displayTitle = title ?: name
    require(!displayTitle.isNullOrBlank())
    require(id > 0)
    return SeerrMediaSummary(
        tmdbId = id,
        mediaType = type,
        title = displayTitle,
        overview = overview,
        posterPath = posterPath ?: profilePath,
        backdropPath = backdropPath,
        releaseDate = releaseDate ?: firstAirDate,
        voteAverage = voteAverage,
        mediaInfo = mediaInfo?.toDomain(),
    )
}

private fun String.toMediaType(): SeerrMediaType =
    when (this) {
        "movie" -> SeerrMediaType.MOVIE
        "tv" -> SeerrMediaType.TV
        "person" -> SeerrMediaType.PERSON
        else -> throw IllegalArgumentException("Unsupported Seerr media type")
    }

internal fun SeerrMovieDetailsDto.toDomain(): SeerrMovieDetails {
    require(id > 0 && title.isNotBlank())
    return SeerrMovieDetails(
        tmdbId = id,
        title = title,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        releaseDate = releaseDate,
        runtimeMinutes = runtime,
        genres = genres.map { it.toDomain() },
        mediaInfo = mediaInfo?.toDomain(),
    )
}

internal fun SeerrTvDetailsDto.toDomain(): SeerrTvDetails {
    require(id > 0 && name.isNotBlank())
    return SeerrTvDetails(
        tmdbId = id,
        title = name,
        overview = overview,
        posterPath = posterPath,
        backdropPath = backdropPath,
        firstAirDate = firstAirDate,
        genres = genres.map { it.toDomain() },
        seasons = seasons.map { it.toDomain() },
        mediaInfo = mediaInfo?.toDomain(),
    )
}

private fun SeerrGenreDto.toDomain(): SeerrGenre = SeerrGenre(id, name)

private fun SeerrSeasonDto.toDomain(): SeerrSeason =
    SeerrSeason(
        id = id,
        name = name,
        seasonNumber = seasonNumber,
        episodeCount = episodeCount,
        overview = overview,
        posterPath = posterPath,
    )

internal fun SeerrMediaInfoDto.toDomain(): SeerrMediaInfo =
    SeerrMediaInfo(
        id = id,
        tmdbId = tmdbId,
        tvdbId = tvdbId,
        status = SeerrAvailabilityStatus(status),
        requests = requests.map { it.toDomain() },
    )

private fun SeerrEmbeddedRequestDto.toDomain(): SeerrMediaRequest =
    SeerrMediaRequest(
        id = id,
        status = SeerrRequestStatus(status),
        is4k = is4k,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun SeerrMediaRequestDto.toDomain(): SeerrMediaRequest =
    SeerrMediaRequest(
        id = id,
        status = SeerrRequestStatus(status),
        media = media?.toDomain(),
        is4k = is4k,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun SeerrRequestPageDto.toDomain(): SeerrRequestPage =
    SeerrRequestPage(
        pageInfo = SeerrRequestPageInfo(pageInfo.page, pageInfo.pages, pageInfo.results),
        results = results.map { it.toDomain() },
    )
