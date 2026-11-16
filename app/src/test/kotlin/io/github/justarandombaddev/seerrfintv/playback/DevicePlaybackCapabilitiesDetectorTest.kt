package io.github.justarandombaddev.seerrfintv.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevicePlaybackCapabilitiesDetectorTest {
    @Test
    fun `detector maps only proven MediaCodec and Media3 capabilities`() {
        val probe =
            FakePlaybackCapabilityProbe(
                videoDecoders =
                    mapOf(
                        MimeTypes.VIDEO_H264 to
                            PlatformVideoDecoder(
                                mimeType = MimeTypes.VIDEO_H264,
                                maxWidth = 1_920,
                                maxHeight = 1_080,
                                maxBitrate = 20_000_000,
                                profiles = setOf("baseline", "main", "high"),
                            maxLevel = 51,
                            ),
                        MimeTypes.VIDEO_H265 to
                            PlatformVideoDecoder(
                                mimeType = MimeTypes.VIDEO_H265,
                                maxWidth = 3_840,
                                maxHeight = 2_160,
                                maxBitrate = 90_000_000,
                                profiles = setOf("main", "main 10"),
                            maxLevel = 153,
                            ),
                    ),
                audioMimes = setOf(MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_E_AC3),
                audioChannels = 6,
                subtitleMimes = setOf(MimeTypes.APPLICATION_SUBRIP, MimeTypes.TEXT_VTT),
            )

        val capabilities = DevicePlaybackCapabilitiesDetector(probe).detect()

        assertEquals(listOf("h264", "hevc"), capabilities.videoCodecs.map { it.codec })
        assertEquals(setOf("aac", "aac_latm", "eac3"), capabilities.audioCodecs)
        assertFalse("An unreported decoder must stay absent", capabilities.audioCodecs.contains("opus"))
        assertEquals(6, capabilities.maxAudioChannels)
        assertEquals(setOf("srt", "subrip", "vtt", "webvtt"), capabilities.embeddedSubtitleFormats)
        assertEquals(setOf("vtt", "webvtt"), capabilities.hlsSubtitleFormats)
        assertEquals(90_000_000, capabilities.maxStreamingBitrate)
    }

    @Test
    fun `profile constrained codec without a measured level is not advertised`() {
        val probe =
            FakePlaybackCapabilityProbe(
                videoDecoders =
                    mapOf(
                        MimeTypes.VIDEO_H264 to
                            PlatformVideoDecoder(
                                mimeType = MimeTypes.VIDEO_H264,
                                maxWidth = 1_920,
                                maxHeight = 1_080,
                                maxBitrate = 20_000_000,
                                profiles = setOf("high"),
                                maxLevel = null,
                            ),
                    ),
                audioMimes = setOf(MimeTypes.AUDIO_AAC),
                audioChannels = 2,
                subtitleMimes = emptySet(),
            )

        assertEquals(emptyList<VideoCodecCapability>(), DevicePlaybackCapabilitiesDetector(probe).detect().videoCodecs)
    }

    @Test
    fun `capability query failure produces an empty direct profile instead of crashing`() {
        val failingProbe =
            object : PlaybackCapabilityProbe {
                override fun findVideoDecoder(mimeType: String): PlatformVideoDecoder = error("query failed")

                override fun hasAudioDecoder(mimeType: String): Boolean = error("query failed")

                override fun maxAudioChannelCount(): Int = error("query failed")

                override fun supportsSubtitle(mimeType: String): Boolean = error("query failed")
            }

        val capabilities = DevicePlaybackCapabilitiesDetector(failingProbe).detect()

        assertEquals(emptyList<VideoCodecCapability>(), capabilities.videoCodecs)
        assertEquals(emptySet<String>(), capabilities.audioCodecs)
        assertEquals(emptySet<String>(), capabilities.embeddedSubtitleFormats)
        assertEquals(1, capabilities.maxAudioChannels)
    }

    private data class FakePlaybackCapabilityProbe(
        val videoDecoders: Map<String, PlatformVideoDecoder>,
        val audioMimes: Set<String>,
        val audioChannels: Int,
        val subtitleMimes: Set<String>,
    ) : PlaybackCapabilityProbe {
        override fun findVideoDecoder(mimeType: String): PlatformVideoDecoder? = videoDecoders[mimeType]

        override fun hasAudioDecoder(mimeType: String): Boolean = mimeType in audioMimes

        override fun maxAudioChannelCount(): Int = audioChannels

        override fun supportsSubtitle(mimeType: String): Boolean = mimeType in subtitleMimes
    }
}
