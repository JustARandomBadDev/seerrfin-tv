package io.github.justarandombaddev.seerrfintv.playback

data class VideoCodecCapability(
    val codec: String,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitrate: Int,
    val profiles: Set<String> = emptySet(),
    val maxLevel: Int? = null,
)

data class DevicePlaybackCapabilities(
    val videoCodecs: List<VideoCodecCapability>,
    val audioCodecs: Set<String>,
    val maxAudioChannels: Int,
    val embeddedSubtitleFormats: Set<String>,
    val hlsSubtitleFormats: Set<String>,
    val maxStreamingBitrate: Int,
)
