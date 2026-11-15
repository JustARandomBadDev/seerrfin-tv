package io.github.justarandombaddev.seerrfintv.feature.player

import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlayableItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReport
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackReportKind
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngine
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineError
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineSnapshot
import io.github.justarandombaddev.seerrfintv.playback.PlaybackEngineStatus
import io.github.justarandombaddev.seerrfintv.test.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    fun `active playback reports start periodic progress pause and one final stop in order`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reports = mutableListOf<JellyfinPlaybackReport>()
            var elapsedMs = 0L
            val engine = FakePlaybackEngine()
            val viewModel =
                PlayerViewModel(
                    reportPlayback = reports::add,
                    elapsedRealtimeMs = { elapsedMs },
                )

            viewModel.attach(engine, source())
            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Playing,
                    positionMs = 12_345,
                    durationMs = 60_000,
                    isSeekable = true,
                ),
            )
            runCurrent()

            assertEquals(listOf(JellyfinPlaybackReportKind.Start), reports.map(JellyfinPlaybackReport::kind))
            assertEquals(12_345L, reports.single().positionMs)

            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Playing,
                    positionMs = 15_345,
                    durationMs = 60_000,
                    isSeekable = true,
                ),
            )
            elapsedMs = 3_000
            runCurrent()

            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Paused,
                    positionMs = 16_000,
                    durationMs = 60_000,
                    isSeekable = true,
                ),
            )
            runCurrent()
            viewModel.detach(engine)
            runCurrent()
            viewModel.detach(engine)
            runCurrent()

            assertEquals(
                listOf(
                    JellyfinPlaybackReportKind.Start,
                    JellyfinPlaybackReportKind.Progress,
                    JellyfinPlaybackReportKind.Progress,
                    JellyfinPlaybackReportKind.Stop,
                ),
                reports.map(JellyfinPlaybackReport::kind),
            )
            assertEquals(15_345L, reports[1].positionMs)
            assertEquals(true, reports[2].isPaused)
            assertEquals(16_000L, reports.last().positionMs)
        }

    @Test
    fun `playback error reports failed stop and reporting failure does not replace safe player state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reports = mutableListOf<JellyfinPlaybackReport>()
            var attempt = 0
            val engine = FakePlaybackEngine()
            val viewModel =
                PlayerViewModel(
                    reportPlayback = { report ->
                        attempt += 1
                        if (attempt == 1) error("synthetic reporting failure")
                        reports += report
                    },
                )

            viewModel.attach(engine, source())
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing, positionMs = 5_000))
            runCurrent()
            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Error,
                    positionMs = 7_000,
                    error = PlaybackEngineError.PlaybackFailed,
                ),
            )
            runCurrent()

            assertEquals(PlayerStatus.Error, viewModel.uiState.value.status)
            assertEquals(PlayerUiError.PlaybackFailed, viewModel.uiState.value.error)

            viewModel.detach(engine)
            runCurrent()

            assertEquals(listOf(JellyfinPlaybackReportKind.Stop), reports.map(JellyfinPlaybackReport::kind))
            assertTrue(reports.single().failed)
            assertEquals(7_000L, reports.single().positionMs)
        }

    @Test
    fun `replay after ended opens and closes a new Jellyfin reporting sequence`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reports = mutableListOf<JellyfinPlaybackReport>()
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel(reportPlayback = reports::add)
            viewModel.attach(engine, source())

            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing, positionMs = 50_000))
            runCurrent()
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Ended, positionMs = 60_000))
            runCurrent()
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing, positionMs = 0))
            runCurrent()
            viewModel.detach(engine)
            runCurrent()

            assertEquals(
                listOf(
                    JellyfinPlaybackReportKind.Start,
                    JellyfinPlaybackReportKind.Stop,
                    JellyfinPlaybackReportKind.Start,
                    JellyfinPlaybackReportKind.Stop,
                ),
                reports.map(JellyfinPlaybackReport::kind),
            )
            assertEquals(listOf(50_000L, 60_000L, 0L, 0L), reports.map(JellyfinPlaybackReport::positionMs))
        }

    @Test
    fun `consecutive engine attachments serialize old stop before new start`() =
        runTest(mainDispatcherRule.dispatcher) {
            val firstDeliveryStarted = CompletableDeferred<Unit>()
            val releaseFirstDelivery = CompletableDeferred<Unit>()
            val delivered = mutableListOf<JellyfinPlaybackReport>()
            val firstEngine = FakePlaybackEngine()
            val secondEngine = FakePlaybackEngine()
            val firstSource = source()
            val secondSource =
                source().copy(
                    item = source().item.copy(id = "00000000-0000-0000-0000-000000000200"),
                )
            val viewModel =
                PlayerViewModel(
                    reportPlayback = { report ->
                        delivered += report
                        if (report.kind == JellyfinPlaybackReportKind.Start && report.itemId == firstSource.item.id) {
                            firstDeliveryStarted.complete(Unit)
                            releaseFirstDelivery.await()
                        }
                    },
                )

            viewModel.attach(firstEngine, firstSource)
            firstEngine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing, positionMs = 5_000))
            runCurrent()
            firstDeliveryStarted.await()

            viewModel.attach(secondEngine, secondSource)
            secondEngine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Playing, positionMs = 8_000))
            runCurrent()

            assertEquals(listOf(firstSource.item.id), delivered.map(JellyfinPlaybackReport::itemId))

            releaseFirstDelivery.complete(Unit)
            runCurrent()
            viewModel.detach(secondEngine)
            runCurrent()

            assertEquals(
                listOf(
                    JellyfinPlaybackReportKind.Start to firstSource.item.id,
                    JellyfinPlaybackReportKind.Stop to firstSource.item.id,
                    JellyfinPlaybackReportKind.Start to secondSource.item.id,
                    JellyfinPlaybackReportKind.Stop to secondSource.item.id,
                ),
                delivered.map { it.kind to it.itemId },
            )
        }

    @Test
    fun `paused playback sends a heartbeat after fifteen seconds without position change`() =
        runTest(mainDispatcherRule.dispatcher) {
            val reports = mutableListOf<JellyfinPlaybackReport>()
            var elapsedMs = 0L
            val engine = FakePlaybackEngine()
            val viewModel =
                PlayerViewModel(
                    reportPlayback = reports::add,
                    elapsedRealtimeMs = { elapsedMs },
                )
            viewModel.attach(engine, source())

            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Paused,
                    positionMs = 12_345,
                    updateSequence = 1,
                ),
            )
            runCurrent()
            elapsedMs = 15_000
            engine.emit(
                PlaybackEngineSnapshot(
                    status = PlaybackEngineStatus.Paused,
                    positionMs = 12_345,
                    updateSequence = 2,
                ),
            )
            runCurrent()
            viewModel.detach(engine)
            runCurrent()

            assertEquals(
                listOf(
                    JellyfinPlaybackReportKind.Start,
                    JellyfinPlaybackReportKind.Progress,
                    JellyfinPlaybackReportKind.Stop,
                ),
                reports.map(JellyfinPlaybackReport::kind),
            )
            assertTrue(reports[1].isPaused)
        }

    @Test
    fun `remote commands delegate play pause toggle and bounded seek increments`() =
        runTest(mainDispatcherRule.dispatcher) {
            val engine = FakePlaybackEngine()
            val viewModel = PlayerViewModel()
            viewModel.attach(engine, source())
            engine.emit(PlaybackEngineSnapshot(status = PlaybackEngineStatus.Paused, isSeekable = true))
            runCurrent()

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
            item =
                JellyfinPlayableItem(
                    id = "00000000-0000-0000-0000-000000000100",
                    name = "Test film",
                    type = "Movie",
                    resumePositionMs = 12_345,
                ),
            mediaSourceId = "source-id",
            playSessionId = "play-session-id",
            method = PlaybackMethod.TRANSCODE,
            container = "ts",
            url = "https://media.example.test/video.m3u8?token=secret-header",
            requiredHttpHeaders = mapOf("Authorization" to "secret-header"),
        )
}
