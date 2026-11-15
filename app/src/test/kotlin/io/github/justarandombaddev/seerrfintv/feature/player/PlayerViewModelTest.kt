package io.github.justarandombaddev.seerrfintv.feature.player

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngine
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineError
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineSnapshot
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineStatus
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `attaching prepares the negotiated source and exposes engine state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()

            viewModel.attach(engine, source())
            runCurrent()
            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Playing,
                    positionMs = 12_000,
                    durationMs = 60_000,
                    isSeekable = true,
                ),
            )
            runCurrent()

            assertEquals(listOf(source()), engine.preparedSources)
            assertEquals(PlayerStatus.Playing, viewModel.uiState.value.status)
            assertEquals(12_000, viewModel.uiState.value.positionMs)
            assertEquals(60_000, viewModel.uiState.value.durationMs)
            assertTrue(viewModel.uiState.value.isSeekable)
            assertNull(viewModel.uiState.value.error)
        }

    @Test
    fun `remote commands delegate play pause toggle and bounded seek increments`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()
            viewModel.attach(engine, source())
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Paused, isSeekable = true))
            advanceUntilIdle()

            viewModel.onCommand(PlayerCommand.Toggle)
            viewModel.onCommand(PlayerCommand.Play)
            viewModel.onCommand(PlayerCommand.Pause)
            viewModel.onCommand(PlayerCommand.SeekBack)
            viewModel.onCommand(PlayerCommand.SeekForward)

            assertEquals(1, engine.toggleCalls)
            assertEquals(1, engine.playCalls)
            assertEquals(1, engine.pauseCalls)
            assertEquals(listOf(-10_000L, 10_000L), engine.seekOffsets)
        }

    @Test
    fun `non seekable state ignores seek commands`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()
            viewModel.attach(engine, source())
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Paused, isSeekable = false))
            runCurrent()

            viewModel.onCommand(PlayerCommand.SeekForward)

            assertTrue(engine.seekOffsets.isEmpty())
        }

    @Test
    fun `playback failures expose a safe error without source details`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()
            viewModel.attach(engine, source())
            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Error,
                    error = PlaybackEngineError.PlaybackFailed,
                ),
            )
            runCurrent()

            assertEquals(PlayerStatus.Error, viewModel.uiState.value.status)
            assertEquals(PlayerUiError.PlaybackFailed, viewModel.uiState.value.error)
            assertFalse(viewModel.uiState.value.toString().contains("media.example.test"))
            assertFalse(viewModel.uiState.value.toString().contains("secret-header"))
        }

    @Test
    fun `detaching stops observation and commands`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()
            viewModel.attach(engine, source())
            runCurrent()

            viewModel.detach(engine)
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing))
            runCurrent()
            viewModel.onCommand(PlayerCommand.Play)

            assertEquals(PlayerStatus.Idle, viewModel.uiState.value.status)
            assertEquals(0, engine.playCalls)
        }

    private class FakePlaybackEngine : PlaybackEngine {
        private val mutableState = MutableStateFlow(PlaybackEngineSnapshot())
        override val state: StateFlow<PlaybackEngineSnapshot> = mutableState
        val preparedSources = mutableListOf<JellyfinPlaybackSource>()
        val seekOffsets = mutableListOf<Long>()
        var toggleCalls = 0
        var playCalls = 0
        var pauseCalls = 0

        override fun prepare(source: JellyfinPlaybackSource) {
            preparedSources += source
        }

        override fun togglePlayPause() {
            toggleCalls += 1
        }

        override fun play() {
            playCalls += 1
        }

        override fun pause() {
            pauseCalls += 1
        }

        override fun seekBy(offsetMs: Long) {
            seekOffsets += offsetMs
        }

        override fun release() = Unit

        fun emit(snapshot: PlaybackEngineSnapshot) {
            mutableState.value = snapshot
        }
    }

    private fun source() =
        JellyfinPlaybackSource(
            item = JellyfinPlayableItem(id = "item-id", name = "Test film", type = "Movie"),
            mediaSourceId = "source-id",
            playSessionId = "play-session-id",
            method = PlaybackMethod.TRANSCODE,
            container = "ts",
            url = "https://media.example.test/video.m3u8?token=secret-header",
            requiredHttpHeaders = mapOf("Authorization" to "secret-header"),
        )
}
