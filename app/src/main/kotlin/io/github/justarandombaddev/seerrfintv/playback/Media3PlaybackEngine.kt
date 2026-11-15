package io.github.justarandombaddev.seerrfintv.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val POSITION_REFRESH_MS = 500L

class Media3PlaybackEngine(context: Context) : PlaybackEngine {
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
    private val exoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
    private val mediaSession = MediaSession.Builder(context.applicationContext, exoPlayer).build()
    private val mutableState = MutableStateFlow(PlaybackEngineSnapshot())
    override val state: StateFlow<PlaybackEngineSnapshot> = mutableState.asStateFlow()
    internal val player: Player = exoPlayer
    private val handler = Handler(Looper.getMainLooper())
    private var released = false
    private var playbackFailed = false
    private var snapshotSequence = 0L

    private val positionRefresh =
        object : Runnable {
            override fun run() {
                publishSnapshot()
                if (!released) handler.postDelayed(this, POSITION_REFRESH_MS)
            }
        }

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) = publishSnapshot()

            override fun onIsPlayingChanged(isPlaying: Boolean) = publishSnapshot()

            override fun onPlayerError(error: PlaybackException) {
                playbackFailed = true
                publishSnapshot()
            }

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int,
            ) = publishSnapshot()
        }

    init {
        exoPlayer.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true,
        )
        exoPlayer.addListener(listener)
        handler.post(positionRefresh)
    }

    override fun prepare(source: JellyfinPlaybackSource) {
        check(!released)
        playbackFailed = false
        mutableState.value = PlaybackEngineSnapshot(status = PlaybackEngineStatus.Preparing)
        httpDataSourceFactory
            .setReadTimeoutMs(playbackReadTimeoutMs(source.method))
            .setDefaultRequestProperties(source.requiredHttpHeaders)
        val mediaItem =
            MediaItem.Builder()
                .setUri(source.url)
                .setMediaId(source.item.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(source.item.name).build())
                .build()
        exoPlayer.setMediaItem(mediaItem, resumeStartPositionMs(source.item.resumePositionMs))
        exoPlayer.prepare()
        exoPlayer.play()
    }

    override fun togglePlayPause() {
        when (
            playbackToggleAction(
                playWhenReady = exoPlayer.playWhenReady,
                playbackEnded = exoPlayer.playbackState == Player.STATE_ENDED,
            )
        ) {
            PlaybackToggleAction.Play -> play()
            PlaybackToggleAction.Pause -> pause()
        }
    }

    override fun play() {
        if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0)
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekBy(offsetMs: Long) {
        if (!exoPlayer.isCurrentMediaItemSeekable ||
            !exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        ) {
            return
        }
        exoPlayer.seekTo(
            boundedSeekTarget(
                currentMs = exoPlayer.currentPosition,
                offsetMs = offsetMs,
                durationMs = exoPlayer.safeDuration(),
            ),
        )
    }

    override fun release() {
        if (released) return
        released = true
        handler.removeCallbacks(positionRefresh)
        exoPlayer.removeListener(listener)
        mediaSession.release()
        exoPlayer.release()
        mutableState.value = PlaybackEngineSnapshot()
    }

    private fun publishSnapshot() {
        if (released) return
        snapshotSequence += 1
        val status =
            when {
                playbackFailed -> PlaybackEngineStatus.Error
                exoPlayer.playbackState == Player.STATE_ENDED -> PlaybackEngineStatus.Ended
                exoPlayer.isPlaying -> PlaybackEngineStatus.Playing
                exoPlayer.playbackState == Player.STATE_IDLE -> PlaybackEngineStatus.Idle
                exoPlayer.playbackState == Player.STATE_BUFFERING -> PlaybackEngineStatus.Preparing
                else -> PlaybackEngineStatus.Paused
            }
        mutableState.value =
            PlaybackEngineSnapshot(
                status = status,
                positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                durationMs = exoPlayer.safeDuration(),
                isSeekable =
                    exoPlayer.isCurrentMediaItemSeekable &&
                        exoPlayer.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                error = if (playbackFailed) PlaybackEngineError.PlaybackFailed else null,
                updateSequence = snapshotSequence,
            )
    }
}

private fun Player.safeDuration(): Long = duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0) ?: 0
