package io.github.justarandombaddev.seerrfintv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReportKind
import java.util.concurrent.CancellationException
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngine
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineError
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineSnapshot
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SEEK_INCREMENT_MS = 10_000L
private const val PLAYBACK_PROGRESS_INTERVAL_MS = 3_000L
private const val PAUSED_PROGRESS_INTERVAL_MS = 15_000L

enum class PlayerStatus {
    Idle,
    Preparing,
    Playing,
    Paused,
    Ended,
    Error,
}

enum class PlayerUiError {
    PlaybackFailed,
}

data class PlayerUiState(
    val status: PlayerStatus = PlayerStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isSeekable: Boolean = false,
    val error: PlayerUiError? = null,
)

class PlayerViewModel(
    private val reportPlayback: suspend (JellyfinPlaybackReport) -> Unit = {},
    private val elapsedRealtimeMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()
    private var engine: PlaybackEngine? = null
    private var observation: Job? = null
    private val reportChannel = Channel<JellyfinPlaybackReport>(Channel.UNLIMITED)
    private var source: JellyfinPlaybackSource? = null
    private var latestSnapshot = PlaybackEngineSnapshot()
    private var playbackStarted = false
    private var playbackStopped = false
    private var lastActiveStatus: PlaybackEngineStatus? = null
    private var lastProgressReportAtMs = 0L

    init {
        viewModelScope.launch {
            for (report in reportChannel) {
                try {
                    reportPlayback(report)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Playback reporting is best effort and must not interrupt the player.
                }
            }
        }
    }

    fun attach(
        engine: PlaybackEngine,
        source: JellyfinPlaybackSource,
    ) {
        if (this.engine === engine) return
        this.engine?.let(::detach)
        this.engine = engine
        this.source = source
        latestSnapshot = PlaybackEngineSnapshot()
        playbackStarted = false
        playbackStopped = false
        lastActiveStatus = null
        lastProgressReportAtMs = 0
        observation =
            viewModelScope.launch {
                engine.state.collect { snapshot ->
                    latestSnapshot = snapshot
                    mutableUiState.value = snapshot.toUiState()
                    onPlaybackSnapshot(snapshot)
                }
            }
        engine.prepare(source)
    }

    fun detach(engine: PlaybackEngine) {
        if (this.engine !== engine) return
        observation?.cancel()
        observation = null
        stopReporting(latestSnapshot)
        this.engine = null
        source = null
        mutableUiState.value = PlayerUiState()
    }

    fun onCommand(command: PlayerCommand) {
        val activeEngine = engine ?: return
        when (command) {
            PlayerCommand.Toggle -> activeEngine.togglePlayPause()
            PlayerCommand.Play -> activeEngine.play()
            PlayerCommand.Pause -> activeEngine.pause()
            PlayerCommand.SeekBack -> if (mutableUiState.value.isSeekable) activeEngine.seekBy(-SEEK_INCREMENT_MS)
            PlayerCommand.SeekForward -> if (mutableUiState.value.isSeekable) activeEngine.seekBy(SEEK_INCREMENT_MS)
        }
    }

    private fun onPlaybackSnapshot(snapshot: PlaybackEngineSnapshot) {
        when (snapshot.status) {
            PlaybackEngineStatus.Playing,
            PlaybackEngineStatus.Paused,
            -> {
                val nowMs = elapsedRealtimeMs()
                if (!playbackStarted || playbackStopped) {
                    playbackStarted = true
                    playbackStopped = false
                    lastActiveStatus = snapshot.status
                    lastProgressReportAtMs = nowMs
                    enqueueReport(JellyfinPlaybackReportKind.Start, snapshot)
                } else if (lastActiveStatus != snapshot.status) {
                    lastActiveStatus = snapshot.status
                    lastProgressReportAtMs = nowMs
                    enqueueReport(JellyfinPlaybackReportKind.Progress, snapshot)
                } else {
                    val intervalMs =
                        if (snapshot.status == PlaybackEngineStatus.Playing) {
                            PLAYBACK_PROGRESS_INTERVAL_MS
                        } else {
                            PAUSED_PROGRESS_INTERVAL_MS
                        }
                    if (nowMs - lastProgressReportAtMs >= intervalMs) {
                        lastProgressReportAtMs = nowMs
                        enqueueReport(JellyfinPlaybackReportKind.Progress, snapshot)
                    }
                }
            }

            PlaybackEngineStatus.Ended,
            PlaybackEngineStatus.Error,
            -> stopReporting(snapshot)

            PlaybackEngineStatus.Idle,
            PlaybackEngineStatus.Preparing,
            -> Unit
        }
    }

    private fun stopReporting(snapshot: PlaybackEngineSnapshot) {
        if (!playbackStarted || playbackStopped) return
        playbackStopped = true
        enqueueReport(JellyfinPlaybackReportKind.Stop, snapshot)
    }

    private fun enqueueReport(
        kind: JellyfinPlaybackReportKind,
        snapshot: PlaybackEngineSnapshot,
    ) {
        val activeSource = source ?: return
        reportChannel.trySend(
            JellyfinPlaybackReport(
                kind = kind,
                itemId = activeSource.item.id,
                mediaSourceId = activeSource.mediaSourceId,
                playSessionId = activeSource.playSessionId,
                method = activeSource.method,
                positionMs = snapshot.positionMs.coerceAtLeast(0),
                isPaused = snapshot.status == PlaybackEngineStatus.Paused,
                isSeekable = snapshot.isSeekable,
                failed = kind == JellyfinPlaybackReportKind.Stop && snapshot.status == PlaybackEngineStatus.Error,
            ),
        )
    }
}

private fun PlaybackEngineSnapshot.toUiState(): PlayerUiState =
    PlayerUiState(
        status =
            when (status) {
                PlaybackEngineStatus.Idle -> PlayerStatus.Idle
                PlaybackEngineStatus.Preparing -> PlayerStatus.Preparing
                PlaybackEngineStatus.Playing -> PlayerStatus.Playing
                PlaybackEngineStatus.Paused -> PlayerStatus.Paused
                PlaybackEngineStatus.Ended -> PlayerStatus.Ended
                PlaybackEngineStatus.Error -> PlayerStatus.Error
            },
        positionMs = positionMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(0),
        isSeekable = isSeekable,
        error =
            when (error) {
                PlaybackEngineError.PlaybackFailed -> PlayerUiError.PlaybackFailed
                null -> null
            },
    )
