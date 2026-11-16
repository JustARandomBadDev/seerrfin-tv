package io.github.justarandombaddev.seerrfintv.domain.model

enum class JellyfinCatalogItemType {
    Movie,
    Series,
    Episode,
    Video,
}

data class JellyfinCatalogItem(
    val id: String,
    val name: String,
    val type: JellyfinCatalogItemType,
    val productionYear: Int? = null,
    val playbackPositionTicks: Long = 0,
    val indexNumber: Int? = null,
    val parentIndexNumber: Int? = null,
)

data class JellyfinSeason(
    val id: String,
    val name: String,
    val indexNumber: Int?,
)

data class JellyfinHomeRow(
    val library: JellyfinLibrary,
    val items: List<JellyfinCatalogItem>,
)
