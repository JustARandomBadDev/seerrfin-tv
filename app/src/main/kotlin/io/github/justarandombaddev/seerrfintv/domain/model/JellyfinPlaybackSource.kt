package io.github.justarandombaddev.seerrfintv.domain.model

data class JellyfinPlayableItem(
    val id: String,
    val name: String,
    val type: String,
) {
    override fun toString(): String = "JellyfinPlayableItem(<redacted>)"
}

enum class PlaybackMethod {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE,
}

data class JellyfinPlaybackSource(
    val item: JellyfinPlayableItem,
    val mediaSourceId: String,
    val playSessionId: String?,
    val method: PlaybackMethod,
    val container: String?,
    val url: String,
    val requiredHttpHeaders: Map<String, String>,
) {
    override fun toString(): String = "JellyfinPlaybackSource(<redacted>)"
}
