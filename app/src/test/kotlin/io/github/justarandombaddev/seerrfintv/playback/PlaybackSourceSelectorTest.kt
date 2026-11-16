package io.github.justarandombaddev.seerrfintv.playback

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceSelectorTest {
    @Test
    fun `direct play wins over earlier direct stream and transcode sources`() {
        val result =
            PlaybackSourceSelector.select(
                item = item(),
                playSessionId = "play-session",
                sources =
                    listOf(
                        candidate(
                            mediaSourceId = "stream-source",
                            supportsDirectStream = true,
                            supportsTranscoding = true,
                            transcodingUrl = "https://media.example.test/stream.m3u8?api_key=secret",
                        ),
                        candidate(
                            mediaSourceId = "direct-source",
                            supportsDirectPlay = true,
                            directPlayUrl = "https://media.example.test/video.mp4?api_key=secret",
                        ),
                    ),
            )

        assertEquals(PlaybackMethod.DIRECT_PLAY, result?.method)
        assertEquals("direct-source", result?.mediaSourceId)
    }

    @Test
    fun `unavailable methods and capability flags without required URLs are skipped`() {
        val result =
            PlaybackSourceSelector.select(
                item = item(),
                playSessionId = null,
                sources =
                    listOf(
                        candidate(
                            mediaSourceId = "",
                            supportsDirectPlay = true,
                            directPlayUrl = "https://media.example.test/video.mp4",
                        ),
                        candidate(supportsDirectPlay = true, directPlayUrl = ""),
                        candidate(supportsDirectStream = true, transcodingUrl = null),
                        candidate(supportsTranscoding = false, transcodingUrl = "/transcode.m3u8"),
                    ),
            )

        assertNull(result)
    }

    @Test
    fun `direct stream wins over transcode when direct play is unavailable`() {
        val result =
            PlaybackSourceSelector.select(
                item = item(),
                playSessionId = "play-session",
                sources =
                    listOf(
                        candidate(
                            mediaSourceId = "transcode-source",
                            supportsTranscoding = true,
                            transcodingUrl = "https://media.example.test/transcode.m3u8",
                        ),
                        candidate(
                            mediaSourceId = "stream-source",
                            supportsDirectStream = true,
                            transcodingUrl = "https://media.example.test/stream.m3u8",
                        ),
                    ),
            )

        assertEquals(PlaybackMethod.DIRECT_STREAM, result?.method)
        assertEquals("stream-source", result?.mediaSourceId)
    }

    @Test
    fun `server codec rejection falls back to transcode without treating HLS as direct play`() {
        val result =
            PlaybackSourceSelector.select(
                item = item(),
                playSessionId = "play-session",
                sources =
                    listOf(
                        candidate(
                            supportsDirectPlay = false,
                            supportsDirectStream = false,
                            supportsTranscoding = true,
                            directPlayUrl = "https://media.example.test/video.mkv",
                            transcodingUrl = "https://media.example.test/transcode.m3u8",
                        ),
                    ),
            )

        assertEquals(PlaybackMethod.TRANSCODE, result?.method)
        assertEquals("ts", result?.container)
    }

    @Test
    fun `server remote URL is accepted only with direct play capability`() {
        val remoteUrl = "https://remote.example.test/video.mp4"

        val rejected =
            PlaybackSourceSelector.select(
                item(),
                playSessionId = null,
                sources = listOf(candidate(directPlayUrl = remoteUrl)),
            )
        val accepted =
            PlaybackSourceSelector.select(
                item(),
                playSessionId = null,
                sources =
                    listOf(
                        candidate(
                            supportsDirectPlay = true,
                            directPlayUrl = remoteUrl,
                        ),
                    ),
            )

        assertNull(rejected)
        assertEquals(remoteUrl, accepted?.url)
    }

    @Test
    fun `playback source string representation redacts sensitive and personal values`() {
        val result =
            PlaybackSourceSelector.select(
                item = item(),
                playSessionId = "private-play-session",
                sources =
                    listOf(
                        candidate(
                            supportsDirectPlay = true,
                            directPlayUrl = "https://private.example.test/video?api_key=secret-token",
                            requiredHttpHeaders = mapOf("Authorization" to "secret-token"),
                        ),
                    ),
            )
        val rendered = result.toString()

        assertFalse(rendered.contains("private", ignoreCase = true))
        assertFalse(rendered.contains("secret", ignoreCase = true))
        assertFalse(rendered.contains("Example Movie"))
        assertEquals("JellyfinPlaybackSource(<redacted>)", rendered)
        assertEquals("JellyfinPlayableItem(<redacted>)", result?.item.toString())
    }

    private fun item() =
        JellyfinPlayableItem(
            id = "00000000-0000-0000-0000-000000000100",
            name = "Example Movie",
            type = "MOVIE",
        )

    private fun candidate(
        mediaSourceId: String = "media-source",
        supportsDirectPlay: Boolean = false,
        supportsDirectStream: Boolean = false,
        supportsTranscoding: Boolean = false,
        directPlayUrl: String? = null,
        transcodingUrl: String? = null,
        requiredHttpHeaders: Map<String, String> = emptyMap(),
    ) =
        PlaybackSourceCandidate(
            mediaSourceId = mediaSourceId,
            container = "mp4",
            transcodingContainer = "ts",
            supportsDirectPlay = supportsDirectPlay,
            supportsDirectStream = supportsDirectStream,
            supportsTranscoding = supportsTranscoding,
            directPlayUrl = directPlayUrl,
            transcodingUrl = transcodingUrl,
            requiredHttpHeaders = requiredHttpHeaders,
        )
}
