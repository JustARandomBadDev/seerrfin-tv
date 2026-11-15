package io.github.justarandombaddev.seerrfintv.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngine
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineError
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineSnapshot
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val SEEK_INCREMENT_MS = 10_000L

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

class PlayerViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = mutableUiState.asStateFlow()
    private var engine: PlaybackEngine? = null
    private var observation: Job? = null

    fun attach(
        engine: PlaybackEngine,
        source: JellyfinPlaybackSource,
    ) {
        if (this.engine === engine) return
        observation?.cancel()
        this.engine = engine
        observation =
            viewModelScope.launch {
                engine.state.collect { snapshot ->
                    mutableUiState.value = snapshot.toUiState()
                }
            }
        engine.prepare(source)
    }

    fun detach(engine: PlaybackEngine) {
        if (this.engine !== engine) return
        observation?.cancel()
        observation = null
        this.engine = null
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
