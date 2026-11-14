package io.github.justarandombaddev.seerrfintv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.justarandombaddev.seerrfintv.feature.home.BootstrapScreen
import io.github.justarandombaddev.seerrfintv.ui.theme.SeerrfinTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SeerrfinTheme {
                BootstrapScreen()
            }
        }
    }
}
