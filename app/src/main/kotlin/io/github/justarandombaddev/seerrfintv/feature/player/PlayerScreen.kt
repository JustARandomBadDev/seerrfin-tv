package io.github.justarandombaddev.seerrfintv.feature.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinPlaybackSource
import io.github.justarandombaddev.seerrfintv.playback.Media3PlaybackEngine

@Composable
fun PlayerRoute(
    source: JellyfinPlaybackSource,
    createEngine: () -> Media3PlaybackEngine,
    onBack: () -> Unit,
) {
    val engine = remember(source) { createEngine() }
    val playerViewModel = viewModel<PlayerViewModel>(key = "media3-player")
    val state by playerViewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val hostView = LocalView.current

    DisposableEffect(engine, source, lifecycleOwner, hostView) {
        playerViewModel.attach(engine, source)
        val previousKeepScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    playerViewModel.onCommand(PlayerCommand.Pause)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            hostView.keepScreenOn = previousKeepScreenOn
            playerViewModel.detach(engine)
            engine.release()
        }
    }

    PlayerScreen(
        engine = engine,
        state = state,
        onCommand = playerViewModel::onCommand,
        onBack = onBack,
    )
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    engine: Media3PlaybackEngine,
    state: PlayerUiState,
    onCommand: (PlayerCommand) -> Unit,
    onBack: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyInputTracker = remember { PlayerKeyInputTracker() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isKeyDown =
                        when (event.type) {
                            KeyEventType.KeyDown -> true
                            KeyEventType.KeyUp -> false
                            else -> return@onPreviewKeyEvent false
                        }
                    val handling =
                        keyInputTracker.onKeyEvent(
                            keyCode = event.key.nativeKeyCode,
                            isKeyDown = isKeyDown,
                        )
                    handling.command?.let(onCommand)
                    handling.consumed
                }
                .focusable(),
    ) {
        PlayerSurface(
            player = engine.player,
            modifier = Modifier.fillMaxSize(),
        )
        PlayerStatusOverlay(
            state = state,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlayerStatusOverlay(
    state: PlayerUiState,
    modifier: Modifier = Modifier,
) {
    val showOverlay = state.status != PlayerStatus.Playing
    if (!showOverlay) return

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(Color(0xCC0F172A))
                .padding(horizontal = 48.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = playerStatusText(state),
            color = if (state.status == PlayerStatus.Error) Color(0xFFFCA5A5) else Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.player_remote_help),
                color = Color(0xFFCBD5E1),
                fontSize = 18.sp,
            )
            Text(
                text = formatPosition(state.positionMs, state.durationMs),
                color = Color(0xFFCBD5E1),
                fontSize = 18.sp,
            )
        }
    }
}

@Composable
private fun playerStatusText(state: PlayerUiState): String =
    when (state.status) {
        PlayerStatus.Idle,
        PlayerStatus.Preparing,
        -> stringResource(R.string.player_preparing)

        PlayerStatus.Playing -> stringResource(R.string.player_playing)
        PlayerStatus.Paused -> stringResource(R.string.player_paused)
        PlayerStatus.Ended -> stringResource(R.string.player_ended)
        PlayerStatus.Error -> stringResource(R.string.player_error)
    }

internal fun formatPosition(
    positionMs: Long,
    durationMs: Long,
): String = "${formatTime(positionMs)} / ${formatTime(durationMs)}"

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
