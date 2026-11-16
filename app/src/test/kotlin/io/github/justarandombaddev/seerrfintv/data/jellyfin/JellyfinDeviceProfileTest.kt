package io.github.justarandombaddev.seerrfintv.data.jellyfin

import io.github.justarandombaddev.seerrfintv.playback.DevicePlaybackCapabilities
import io.github.justarandombaddev.seerrfintv.playback.VideoCodecCapability
import org.jellyfin.sdk.model.api.CodecType
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.ProfileConditionValue
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinDeviceProfileTest {
    @Test
    fun `profile declares only detected codecs and their measured limits`() {
        val profile = buildPlaybackDeviceProfile(capabilities())
        val videoProfiles = profile.directPlayProfiles.filter { it.type == DlnaProfileType.VIDEO }
        val directVideoCodecs = videoProfiles.flatMap { it.videoCodec.csvValues() }.toSet()
        val directAudioCodecs = videoProfiles.flatMap { it.audioCodec.csvValues() }.toSet()

        assertEquals(setOf("h264", "hevc"), directVideoCodecs)
        assertEquals(setOf("aac", "eac3"), directAudioCodecs)
        assertFalse("An undetected codec must never be advertised", "av1" in directVideoCodecs)
        assertTrue(videoProfiles.any { "mkv" in it.container.csvValues() })

        val hevcProfile = profile.codecProfiles.single { it.codec == "hevc" }
        assertEquals(CodecType.VIDEO, hevcProfile.type)
        assertEquals(
            mapOf(
                ProfileConditionValue.WIDTH to "3840",
                ProfileConditionValue.HEIGHT to "2160",
                ProfileConditionValue.VIDEO_BITRATE to "60000000",
                ProfileConditionValue.VIDEO_PROFILE to "main|main 10",
                ProfileConditionValue.VIDEO_LEVEL to "153",
            ),
            hevcProfile.conditions.associate { it.property to it.value },
        )
        val audioProfile = profile.codecProfiles.single { it.type == CodecType.VIDEO_AUDIO }
        assertEquals("6", audioProfile.conditions.single().value)
        assertEquals(80_000_000, profile.maxStreamingBitrate)
        assertEquals(80_000_000, profile.maxStaticBitrate)
    }

    @Test
    fun `profile keeps HLS remux before transcode and describes subtitle fallbacks`() {
        val profile = buildPlaybackDeviceProfile(capabilities())
        val videoTranscodes = profile.transcodingProfiles.filter { it.type == DlnaProfileType.VIDEO }

        assertEquals(listOf("ts", "mp4"), videoTranscodes.map { it.container })
        assertTrue(videoTranscodes.all { it.protocol == MediaStreamProtocol.HLS })
        assertTrue(videoTranscodes.all { it.enableSubtitlesInManifest })
        assertTrue(videoTranscodes.all { setOf("h264", "hevc") == it.videoCodec.csvValues().toSet() })

        val subtitleMethods =
            profile.subtitleProfiles
                .groupBy({ it.format }, { it.method })
                .mapValues { (_, methods) -> methods.toSet() }
        assertEquals(setOf(SubtitleDeliveryMethod.EMBED), subtitleMethods.getValue("srt"))
        assertEquals(
            setOf(SubtitleDeliveryMethod.EMBED, SubtitleDeliveryMethod.HLS),
            subtitleMethods.getValue("vtt"),
        )
        assertEquals(setOf(SubtitleDeliveryMethod.ENCODE), subtitleMethods.getValue("ass"))
        assertEquals(setOf(SubtitleDeliveryMethod.ENCODE), subtitleMethods.getValue("pgs"))
        assertFalse(
            "External subtitles are not supported until MediaItem subtitle URLs are provided",
            profile.subtitleProfiles.any { it.method == SubtitleDeliveryMethod.EXTERNAL },
        )
    }

    private fun capabilities() =
        DevicePlaybackCapabilities(
            videoCodecs =
                listOf(
                    VideoCodecCapability(
                        codec = "h264",
                        maxWidth = 1_920,
                        maxHeight = 1_080,
                        maxBitrate = 20_000_000,
                        profiles = setOf("baseline", "main", "high"),
                    ),
                    VideoCodecCapability(
                        codec = "hevc",
                        maxWidth = 3_840,
                        maxHeight = 2_160,
                        maxBitrate = 60_000_000,
                        profiles = setOf("main", "main 10"),
                        maxLevel = 153,
                    ),
                ),
            audioCodecs = setOf("aac", "eac3"),
            maxAudioChannels = 6,
            embeddedSubtitleFormats = setOf("srt", "vtt"),
            hlsSubtitleFormats = setOf("vtt"),
            maxStreamingBitrate = 80_000_000,
        )

    private fun String?.csvValues(): List<String> =
        orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
}
