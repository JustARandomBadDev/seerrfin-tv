package io.github.justarandombaddev.seerrfintv.playback

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackEngineStatus {
    Idle,
    Preparing,
    Playing,
    Paused,
    Ended,
    Error,
}

enum class PlaybackEngineError {
    PlaybackFailed,
}

internal enum class PlaybackToggleAction {
    Play,
    Pause,
}

data class PlaybackEngineSnapshot(
    val status: PlaybackEngineStatus = PlaybackEngineStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isSeekable: Boolean = false,
    val error: PlaybackEngineError? = null,
    val updateSequence: Long = 0,
)

interface PlaybackEngine {
    val state: StateFlow<PlaybackEngineSnapshot>

    fun prepare(source: JellyfinPlaybackSource)

    fun togglePlayPause()

    fun play()

    fun pause()

    fun seekBy(offsetMs: Long)

    fun release()
}

internal fun boundedSeekTarget(
    currentMs: Long,
    offsetMs: Long,
    durationMs: Long,
): Long {
    val target = (currentMs + offsetMs).coerceAtLeast(0)
    return if (durationMs > 0) target.coerceAtMost(durationMs) else target
}

internal fun resumeStartPositionMs(positionMs: Long): Long = positionMs.coerceAtLeast(0)

internal fun playbackReadTimeoutMs(method: PlaybackMethod): Int =
    when (method) {
        PlaybackMethod.DIRECT_PLAY,
        PlaybackMethod.DIRECT_STREAM,
        PlaybackMethod.TRANSCODE,
        -> 60_000
    }

internal fun playbackToggleAction(
    playWhenReady: Boolean,
    playbackEnded: Boolean,
): PlaybackToggleAction =
    if (playWhenReady && !playbackEnded) PlaybackToggleAction.Pause else PlaybackToggleAction.Play
