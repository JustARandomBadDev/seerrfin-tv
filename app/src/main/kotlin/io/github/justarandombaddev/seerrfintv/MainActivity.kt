package io.github.justarandombaddev.seerrfintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionScreen
import io.github.justarandombaddev.seerrfintv.feature.connect.ServerConnectionViewModel
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

                ServerConnectionScreen(
                    state = state,
                    onServerUrlChanged = connectionViewModel::onServerUrlChanged,
                    onConnect = connectionViewModel::connect,
                )
            }
        }
    }
}
