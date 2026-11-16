package io.github.justarandombaddev.seerrfintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionScreen
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionStatus
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionViewModel
import io.github.justarandombaddev.seerrfintv.feature.details.JellyfinMediaDetailsScreen
import io.github.justarandombaddev.seerrfintv.feature.home.JellyfinLibrariesScreen
import io.github.justarandombaddev.seerrfintv.feature.home.JellyfinLibrariesViewModel
import io.github.justarandombaddev.seerrfintv.feature.home.LibrariesSessionIssue
import io.github.justarandombaddev.seerrfintv.feature.home.LibrariesStatus
import io.github.justarandombaddev.seerrfintv.feature.home.MediaDetailsStatus
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginNotice
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginScreen
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginStatus
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginViewModel
import io.github.justarandombaddev.seerrfintv.feature.player.PlayerRoute
import io.github.justarandombaddev.seerrfintv.ui.theme.SeerrfinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeerrfinTheme {
                val container = (application as SeerrfinApplication).container
                val connectionViewModel =
                    viewModel {
                        ServerConnectionViewModel(
                            jellyfinRepository = container.jellyfinRepository,
                            settingsRepository = container.serverSettingsRepository,
                            urlNormalizer = container.serverUrlNormalizer,
                        )
                    }
                val state by connectionViewModel.uiState.collectAsStateWithLifecycle()
                val librariesViewModel =
                    viewModel {
                        JellyfinLibrariesViewModel(
                            jellyfinRepository = container.jellyfinRepository,
                            settingsRepository = container.serverSettingsRepository,
                            sessionStore = container.jellyfinSessionStore,
                        )
                    }
                val librariesState by librariesViewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(state.status) {
                    if (state.status == ServerConnectionStatus.Connected) {
                        librariesViewModel.onServerConnected(state.serverUrl)
                    }
                }

                val playbackSource = librariesState.playbackSource
                if (librariesState.status == LibrariesStatus.Success && playbackSource != null) {
                    PlayerRoute(
                        source = playbackSource,
                        createEngine = container::createPlaybackEngine,
                        reportPlayback = librariesViewModel::reportPlayback,
                        onBack = librariesViewModel::closePlayer,
                    )
                } else if (
                    librariesState.status == LibrariesStatus.Success &&
                    librariesState.details.status != MediaDetailsStatus.Closed
                ) {
                    JellyfinMediaDetailsScreen(
                        state = librariesState.details,
                        playbackStatus = librariesState.playbackStatus,
                        onSeasonSelected = librariesViewModel::selectSeason,
                        onPlay = librariesViewModel::resolveItem,
                        onBack = librariesViewModel::closeDetails,
                    )
                } else {
                    when (librariesState.status) {
                        LibrariesStatus.NeedsServer -> {
                            ServerConnectionScreen(
                                state = state,
                                onServerUrlChanged = connectionViewModel::onServerUrlChanged,
                                onConnect = connectionViewModel::connect,
                            )
                        }

                        LibrariesStatus.NeedsLogin -> {
                            val activeLoginServerUrl = requireNotNull(librariesState.serverUrl)
                            val loginViewModel =
                                viewModel<JellyfinLoginViewModel>(
                                    key =
                                        "jellyfin-login-${activeLoginServerUrl.hashCode()}-" +
                                            librariesState.loginRequestId,
                                ) {
                                    JellyfinLoginViewModel(
                                        serverUrl = activeLoginServerUrl,
                                        jellyfinRepository = container.jellyfinRepository,
                                        sessionStore = container.jellyfinSessionStore,
                                    )
                                }
                            val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()

                            LaunchedEffect(loginState.status) {
                                if (loginState.status == JellyfinLoginStatus.Authenticated) {
                                    librariesViewModel.onLoginSucceeded()
                                }
                            }

                            JellyfinLoginScreen(
                                state = loginState,
                                onUsernameChanged = loginViewModel::onUsernameChanged,
                                onPasswordChanged = loginViewModel::onPasswordChanged,
                                onLogin = loginViewModel::login,
                                onBack = {
                                    connectionViewModel.returnToEditing()
                                    librariesViewModel.onLoginBack()
                                },
                                notice =
                                    when (librariesState.sessionIssue) {
                                        LibrariesSessionIssue.Expired -> JellyfinLoginNotice.SessionExpired
                                        LibrariesSessionIssue.Unreadable -> JellyfinLoginNotice.SessionUnreadable
                                        null -> null
                                    },
                            )
                        }

                        LibrariesStatus.Restoring,
                        LibrariesStatus.Loading,
                        LibrariesStatus.Empty,
                        LibrariesStatus.Success,
                        LibrariesStatus.Error,
                        ->
                            JellyfinLibrariesScreen(
                                state = librariesState,
                                onRetry = librariesViewModel::retry,
                                onLibrarySelected = librariesViewModel::selectLibrary,
                                onItemActivated = librariesViewModel::openDetails,
                            )
                    }
                }
            }
        }
    }
}
