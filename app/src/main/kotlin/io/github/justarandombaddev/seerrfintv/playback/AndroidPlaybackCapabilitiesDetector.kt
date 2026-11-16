package io.github.justarandombaddev.seerrfintv.playback

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.text.SubtitleDecoderFactory

internal data class PlatformVideoDecoder(
    val mimeType: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrate: Int,
    val profiles: Set<String>,
    val maxLevel: Int? = null,
)

internal interface PlaybackCapabilityProbe {
    fun findVideoDecoder(mimeType: String): PlatformVideoDecoder?

    fun hasAudioDecoder(mimeType: String): Boolean

    fun maxAudioChannelCount(): Int

    fun supportsSubtitle(mimeType: String): Boolean
}

internal class DevicePlaybackCapabilitiesDetector(
    private val probe: PlaybackCapabilityProbe,
) {
    fun detect(): DevicePlaybackCapabilities {
        val videoCodecs =
            VIDEO_CODECS.mapNotNull { (codec, mimeType) ->
                runCatching { probe.findVideoDecoder(mimeType) }.getOrNull()?.let { decoder ->
                    if (
                        codec in PROFILE_RESTRICTED_CODECS &&
                        (decoder.profiles.isEmpty() || decoder.maxLevel == null)
                    ) {
                        return@let null
                    }
                    VideoCodecCapability(
                        codec = codec,
                        maxWidth = decoder.maxWidth,
                        maxHeight = decoder.maxHeight,
                        maxBitrate = decoder.maxBitrate.coerceAtMost(MAX_STREAMING_BITRATE),
                        profiles = decoder.profiles,
                        maxLevel = decoder.maxLevel,
                    )
                }
            }
        val audioCodecs =
            AUDIO_CODECS
                .filter { (_, mimeType) -> runCatching { probe.hasAudioDecoder(mimeType) }.getOrDefault(false) }
                .flatMapTo(linkedSetOf()) { (codecs, _) -> codecs }
        val embeddedSubtitleFormats =
            SUBTITLE_FORMATS
                .filter { (_, mimeType) -> runCatching { probe.supportsSubtitle(mimeType) }.getOrDefault(false) }
                .flatMapTo(linkedSetOf()) { (formats, _) -> formats }

        return DevicePlaybackCapabilities(
            videoCodecs = videoCodecs,
            audioCodecs = audioCodecs,
            maxAudioChannels =
                runCatching { probe.maxAudioChannelCount() }
                    .getOrDefault(1)
                    .coerceIn(1, MAX_AUDIO_CHANNELS),
            embeddedSubtitleFormats = embeddedSubtitleFormats,
            hlsSubtitleFormats = embeddedSubtitleFormats.intersect(setOf("vtt", "webvtt")),
            maxStreamingBitrate =
                videoCodecs.maxOfOrNull(VideoCodecCapability::maxBitrate)
                    ?.coerceAtMost(MAX_STREAMING_BITRATE)
                    ?: MIN_STREAMING_BITRATE,
        )
    }

    private companion object {
        const val MIN_STREAMING_BITRATE = 20_000_000
        const val MAX_STREAMING_BITRATE = 120_000_000
        const val MAX_AUDIO_CHANNELS = 8

        val PROFILE_RESTRICTED_CODECS = setOf("h264", "hevc")

        val VIDEO_CODECS =
            listOf(
                "h264" to MimeTypes.VIDEO_H264,
                "hevc" to MimeTypes.VIDEO_H265,
                "vp8" to MimeTypes.VIDEO_VP8,
                "vp9" to MimeTypes.VIDEO_VP9,
                "av1" to MimeTypes.VIDEO_AV1,
                "mpeg4" to MimeTypes.VIDEO_MP4V,
                "mpeg2video" to MimeTypes.VIDEO_MPEG2,
            )

        val AUDIO_CODECS =
            listOf(
                setOf("aac", "aac_latm") to MimeTypes.AUDIO_AAC,
                setOf("ac3") to MimeTypes.AUDIO_AC3,
                setOf("eac3") to MimeTypes.AUDIO_E_AC3,
                setOf("mp3") to MimeTypes.AUDIO_MPEG,
                setOf("mp2") to MimeTypes.AUDIO_MPEG_L2,
                setOf("opus") to MimeTypes.AUDIO_OPUS,
                setOf("vorbis") to MimeTypes.AUDIO_VORBIS,
                setOf("flac") to MimeTypes.AUDIO_FLAC,
                setOf("alac") to MimeTypes.AUDIO_ALAC,
                setOf("dts", "dca") to MimeTypes.AUDIO_DTS,
                setOf("truehd", "mlp") to MimeTypes.AUDIO_TRUEHD,
            )

        val SUBTITLE_FORMATS =
            listOf(
                setOf("srt", "subrip") to MimeTypes.APPLICATION_SUBRIP,
                setOf("vtt", "webvtt") to MimeTypes.TEXT_VTT,
                setOf("ttml") to MimeTypes.APPLICATION_TTML,
                setOf("ass", "ssa") to MimeTypes.TEXT_SSA,
                setOf("pgs", "pgssub") to MimeTypes.APPLICATION_PGS,
                setOf("dvdsub") to MimeTypes.APPLICATION_VOBSUB,
                setOf("dvbsub") to MimeTypes.APPLICATION_DVBSUBS,
            )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class AndroidPlaybackCapabilitiesDetector(
    context: Context,
) {
    private val detector =
        DevicePlaybackCapabilitiesDetector(
            AndroidPlaybackCapabilityProbe(context.applicationContext),
        )

    fun detect(): DevicePlaybackCapabilities = detector.detect()
}

@androidx.annotation.OptIn(UnstableApi::class)
private class AndroidPlaybackCapabilityProbe(
    context: Context,
) : PlaybackCapabilityProbe {
    private val codecInfos by lazy { MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.toList() }
    private val audioCapabilities by lazy {
        AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null, emptyList())
    }

    override fun findVideoDecoder(mimeType: String): PlatformVideoDecoder? =
        codecInfos
            .asSequence()
            .filter { codecInfo -> codecInfo.supports(mimeType) && codecInfo.isUsableVideoDecoder() }
            .mapNotNull { codecInfo -> codecInfo.toVideoDecoder(mimeType) }
            .maxByOrNull { decoder -> decoder.maxWidth.toLong() * decoder.maxHeight }

    override fun hasAudioDecoder(mimeType: String): Boolean =
        codecInfos.any { codecInfo -> !codecInfo.isEncoder && codecInfo.supports(mimeType) } ||
            audioCapabilities.isPassthroughPlaybackSupported(
                Format.Builder()
                    .setSampleMimeType(mimeType)
                    .setChannelCount(2)
                    .setSampleRate(48_000)
                    .build(),
                AudioAttributes.DEFAULT,
            )

    override fun maxAudioChannelCount(): Int = audioCapabilities.maxChannelCount

    override fun supportsSubtitle(mimeType: String): Boolean =
        SubtitleDecoderFactory.DEFAULT.supportsFormat(
            Format.Builder().setSampleMimeType(mimeType).build(),
        )

    private fun MediaCodecInfo.toVideoDecoder(mimeType: String): PlatformVideoDecoder? =
        runCatching {
            val codecCapabilities = getCapabilitiesForType(mimeType)
            val videoCapabilities = codecCapabilities.videoCapabilities ?: return null
            val maxSize =
                STANDARD_VIDEO_SIZES.firstOrNull { (width, height) ->
                    runCatching { videoCapabilities.isSizeSupported(width, height) }.getOrDefault(false)
                } ?: return null
            val profiles = codecCapabilities.profileLevels.mapNotNull { profileName(mimeType, it.profile) }.toSet()
            val maxLevel =
                codecCapabilities.profileLevels
                    .mapNotNull { profileLevel ->
                        val profile = profileName(mimeType, profileLevel.profile) ?: return@mapNotNull null
                        val level = levelValue(mimeType, profileLevel.level) ?: return@mapNotNull null
                        profile to level
                    }.groupBy({ it.first }, { it.second })
                    .values
                    .mapNotNull { levels -> levels.maxOrNull() }
                    .minOrNull()
            PlatformVideoDecoder(
                mimeType = mimeType,
                maxWidth = maxSize.first,
                maxHeight = maxSize.second,
                maxBitrate = videoCapabilities.bitrateRange.upper.coerceAtLeast(1),
                profiles = profiles,
                maxLevel = maxLevel,
            )
        }.getOrNull()

    private fun MediaCodecInfo.isUsableVideoDecoder(): Boolean {
        if (isEncoder) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAcceleratedApi29()
        } else {
            !name.startsWith("OMX.google.", ignoreCase = true) &&
                !name.startsWith("c2.android.", ignoreCase = true) &&
                !name.contains("ffmpeg", ignoreCase = true) &&
                !name.endsWith(".sw.decoder", ignoreCase = true)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun MediaCodecInfo.isHardwareAcceleratedApi29(): Boolean = isHardwareAccelerated

    private fun MediaCodecInfo.supports(mimeType: String): Boolean =
        supportedTypes.any { supportedType -> supportedType.equals(mimeType, ignoreCase = true) }

    private fun profileName(
        mimeType: String,
        profile: Int,
    ): String? =
        when (mimeType) {
            MimeTypes.VIDEO_H264 ->
                when (profile) {
                    MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "baseline"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> "constrained baseline"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "main"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "extended"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh,
                    MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh,
                    -> "high"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "high 10"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "high 4:2:2"
                    MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "high 4:4:4"
                    else -> null
                }

            MimeTypes.VIDEO_H265 ->
                when (profile) {
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "main"
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
                    -> "main 10"
                    MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill -> "main still picture"
                    else -> null
                }

            else -> null
        }

    private fun levelValue(
        mimeType: String,
        level: Int,
    ): Int? =
        when (mimeType) {
            MimeTypes.VIDEO_H264 ->
                when (level) {
                    MediaCodecInfo.CodecProfileLevel.AVCLevel1 -> 10
                    MediaCodecInfo.CodecProfileLevel.AVCLevel1b -> 9
                    MediaCodecInfo.CodecProfileLevel.AVCLevel11 -> 11
                    MediaCodecInfo.CodecProfileLevel.AVCLevel12 -> 12
                    MediaCodecInfo.CodecProfileLevel.AVCLevel13 -> 13
                    MediaCodecInfo.CodecProfileLevel.AVCLevel2 -> 20
                    MediaCodecInfo.CodecProfileLevel.AVCLevel21 -> 21
                    MediaCodecInfo.CodecProfileLevel.AVCLevel22 -> 22
                    MediaCodecInfo.CodecProfileLevel.AVCLevel3 -> 30
                    MediaCodecInfo.CodecProfileLevel.AVCLevel31 -> 31
                    MediaCodecInfo.CodecProfileLevel.AVCLevel32 -> 32
                    MediaCodecInfo.CodecProfileLevel.AVCLevel4 -> 40
                    MediaCodecInfo.CodecProfileLevel.AVCLevel41 -> 41
                    MediaCodecInfo.CodecProfileLevel.AVCLevel42 -> 42
                    MediaCodecInfo.CodecProfileLevel.AVCLevel5 -> 50
                    MediaCodecInfo.CodecProfileLevel.AVCLevel51 -> 51
                    MediaCodecInfo.CodecProfileLevel.AVCLevel52 -> 52
                    MediaCodecInfo.CodecProfileLevel.AVCLevel6 -> 60
                    MediaCodecInfo.CodecProfileLevel.AVCLevel61 -> 61
                    MediaCodecInfo.CodecProfileLevel.AVCLevel62 -> 62
                    else -> null
                }

            MimeTypes.VIDEO_H265 -> hevcLevelValue(level)
            else -> null
        }

    private fun hevcLevelValue(level: Int): Int? =
        when (level) {
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel1,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel1,
            -> 30
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel2,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel2,
            -> 60
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel21,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel21,
            -> 63
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel3,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel3,
            -> 90
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel31,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel31,
            -> 93
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel4,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel4,
            -> 120
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel41,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel41,
            -> 123
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel5,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel5,
            -> 150
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel51,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel51,
            -> 153
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel52,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel52,
            -> 156
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel6,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel6,
            -> 180
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel61,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel61,
            -> 183
            MediaCodecInfo.CodecProfileLevel.HEVCMainTierLevel62,
            MediaCodecInfo.CodecProfileLevel.HEVCHighTierLevel62,
            -> 186
            else -> null
        }

    private companion object {
        val STANDARD_VIDEO_SIZES =
            listOf(
                7_680 to 4_320,
                4_096 to 2_160,
                3_840 to 2_160,
                2_560 to 1_440,
                1_920 to 1_080,
                1_280 to 720,
                720 to 576,
            )
    }
}
