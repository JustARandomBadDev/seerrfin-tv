package io.github.justarandombaddev.seerrfintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionScreen
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionStatus
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionViewModel
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginScreen
import io.github.justarandombaddev.seerrfintv.feature.login.JellyfinLoginViewModel
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
                var loginServerUrl by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(state.status) {
                    if (state.status == ServerConnectionStatus.Connected) {
                        loginServerUrl = state.serverUrl
                    }
                }

                val activeLoginServerUrl = loginServerUrl
                if (activeLoginServerUrl == null) {
                    ServerConnectionScreen(
                        state = state,
                        onServerUrlChanged = connectionViewModel::onServerUrlChanged,
                        onConnect = connectionViewModel::connect,
                    )
                } else {
                    val loginViewModel =
                        viewModel<JellyfinLoginViewModel>(
                            key = "jellyfin-login-${activeLoginServerUrl.hashCode()}",
                        ) {
                            JellyfinLoginViewModel(
                                serverUrl = activeLoginServerUrl,
                                jellyfinRepository = container.jellyfinRepository,
                                sessionStore = container.jellyfinSessionStore,
                            )
                        }
                    val loginState by loginViewModel.uiState.collectAsStateWithLifecycle()

                    JellyfinLoginScreen(
                        state = loginState,
                        onUsernameChanged = loginViewModel::onUsernameChanged,
                        onPasswordChanged = loginViewModel::onPasswordChanged,
                        onLogin = loginViewModel::login,
                        onBack = {
                            connectionViewModel.returnToEditing()
                            loginServerUrl = null
                        },
                    )
                }
            }
        }
    }
}
