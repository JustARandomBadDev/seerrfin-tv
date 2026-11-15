package io.github.justarandombaddev.seerrfintv.playback

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod

data class PlaybackSourceCandidate(
    val mediaSourceId: String,
    val container: String?,
    val transcodingContainer: String?,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    val directPlayUrl: String?,
    val transcodingUrl: String?,
    val requiredHttpHeaders: Map<String, String>,
) {
    override fun toString(): String = "PlaybackSourceCandidate(<redacted>)"
}

object PlaybackSourceSelector {
    fun select(
        item: JellyfinPlayableItem,
        playSessionId: String?,
        sources: List<PlaybackSourceCandidate>,
    ): JellyfinPlaybackSource? =
        PlaybackMethod.entries.firstNotNullOfOrNull { method ->
            sources.firstNotNullOfOrNull { source ->
                source.toPlaybackSource(item, playSessionId, method)
            }
        }

    private fun PlaybackSourceCandidate.toPlaybackSource(
        item: JellyfinPlayableItem,
        playSessionId: String?,
        method: PlaybackMethod,
    ): JellyfinPlaybackSource? {
        if (mediaSourceId.isBlank()) return null
        val url =
            when (method) {
                PlaybackMethod.DIRECT_PLAY -> directPlayUrl.takeIf { supportsDirectPlay }
                PlaybackMethod.DIRECT_STREAM -> transcodingUrl.takeIf { supportsDirectStream }
                PlaybackMethod.TRANSCODE -> transcodingUrl.takeIf { supportsTranscoding }
            }?.takeIf(String::isNotBlank) ?: return null

        return JellyfinPlaybackSource(
            item = item,
            mediaSourceId = mediaSourceId,
            playSessionId = playSessionId,
            method = method,
            container =
                when (method) {
                    PlaybackMethod.DIRECT_PLAY -> container
                    PlaybackMethod.DIRECT_STREAM,
                    PlaybackMethod.TRANSCODE,
                    -> transcodingContainer
                },
            url = url,
            requiredHttpHeaders = requiredHttpHeaders,
        )
    }
}
