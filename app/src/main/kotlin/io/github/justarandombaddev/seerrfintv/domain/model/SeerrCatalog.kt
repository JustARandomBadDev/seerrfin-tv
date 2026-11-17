package io.github.justarandombaddev.seerrfintv.domain.model

enum class SeerrMediaType(
    val apiValue: String,
) {
    ALL("all"),
    MOVIE("movie"),
    TV("tv"),
    PERSON("person"),
}

enum class SeerrTimeWindow(
    val apiValue: String,
) {
    DAY("day"),
    WEEK("week"),
}

enum class SeerrRequestFilter(
    val apiValue: String,
) {
    ALL("all"),
    APPROVED("approved"),
    AVAILABLE("available"),
    PENDING("pending"),
    PROCESSING("processing"),
    UNAVAILABLE("unavailable"),
    FAILED("failed"),
    DELETED("deleted"),
    COMPLETED("completed"),
}

enum class SeerrRequestSort(
    val apiValue: String,
) {
    ADDED("added"),
    MODIFIED("modified"),
}

enum class SeerrSortDirection(
    val apiValue: String,
) {
    ASCENDING("asc"),
    DESCENDING("desc"),
}

sealed interface SeerrTvSeasonSelection {
    data object All : SeerrTvSeasonSelection

    data class Selected(
        val seasonNumbers: List<Int>,
    ) : SeerrTvSeasonSelection {
        init {
            require(seasonNumbers.isNotEmpty())
            require(seasonNumbers.all { it >= 0 })
        }
    }
}

data class SeerrPagedResult<T>(
    val page: Int,
    val totalPages: Int,
    val totalResults: Int,
    val results: List<T>,
)

data class SeerrMediaSummary(
    val tmdbId: Long,
    val mediaType: SeerrMediaType,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val voteAverage: Double? = null,
    val mediaInfo: SeerrMediaInfo? = null,
)

data class SeerrGenre(
    val id: Long,
    val name: String,
)

data class SeerrMovieDetails(
    val tmdbId: Long,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val releaseDate: String? = null,
    val runtimeMinutes: Int? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null,
)

data class SeerrTvDetails(
    val tmdbId: Long,
    val title: String,
    val overview: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val firstAirDate: String? = null,
    val genres: List<SeerrGenre> = emptyList(),
    val seasons: List<SeerrSeason> = emptyList(),
    val mediaInfo: SeerrMediaInfo? = null,
)

data class SeerrSeason(
    val id: Long,
    val name: String,
    val seasonNumber: Int,
    val episodeCount: Int? = null,
    val overview: String? = null,
    val posterPath: String? = null,
)

data class SeerrAvailabilityStatus(
    val code: Int,
)

data class SeerrRequestStatus(
    val code: Int,
)

data class SeerrMediaInfo(
    val id: Long,
    val tmdbId: Long,
    val tvdbId: Long? = null,
    val status: SeerrAvailabilityStatus,
    val requests: List<SeerrMediaRequest> = emptyList(),
)

data class SeerrMediaRequest(
    val id: Long,
    val status: SeerrRequestStatus,
    val media: SeerrMediaInfo? = null,
    val is4k: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

data class SeerrRequestPageInfo(
    val page: Int,
    val pages: Int,
    val results: Int,
)

data class SeerrRequestPage(
    val pageInfo: SeerrRequestPageInfo,
    val results: List<SeerrMediaRequest>,
)
