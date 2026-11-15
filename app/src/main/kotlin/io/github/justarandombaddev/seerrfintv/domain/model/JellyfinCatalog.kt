package io.github.justarandombaddev.seerrfintv.domain.model

enum class JellyfinCatalogItemType {
    Movie,
    Series,
    Video,
}

data class JellyfinCatalogItem(
    val id: String,
    val name: String,
    val type: JellyfinCatalogItemType,
    val productionYear: Int? = null,
    val playbackPositionTicks: Long = 0,
)

data class JellyfinHomeRow(
    val library: JellyfinLibrary,
    val items: List<JellyfinCatalogItem>,
)
