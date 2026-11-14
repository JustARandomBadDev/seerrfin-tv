package io.github.justarandombaddev.seerrfintv.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val SeerrfinDarkColors =
    darkColorScheme(
        primary = Color(0xFF7DD3FC),
        background = Color(0xFF10141F),
        surface = Color(0xFF10141F),
        onBackground = Color(0xFFF8FAFC),
        onSurface = Color(0xFFF8FAFC),
    )

@Composable
fun SeerrfinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SeerrfinDarkColors) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xFF10141F)),
        ) {
            content()
        }
    }
}
