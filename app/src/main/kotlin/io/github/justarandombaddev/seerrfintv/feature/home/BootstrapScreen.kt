package io.github.justarandombaddev.seerrfintv.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun BootstrapScreen() {
    var state by remember { mutableStateOf(BootstrapState()) }
    val focusRequesters = remember { List(BootstrapAction.entries.size) { FocusRequester() } }

    LaunchedEffect(Unit) {
        focusRequesters.first().requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 56.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Seerrfin TV",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Bootstrap Android TV · Navigation D-pad",
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )

        Row(
            modifier = Modifier.padding(top = 44.dp).focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BootstrapAction.entries.forEachIndexed { index, action ->
                BootstrapButton(
                    action = action,
                    focusRequester = focusRequesters[index],
                    onActivate = { state = state.activate(action) },
                    modifier =
                        Modifier.focusProperties {
                            when (index) {
                                0 -> {
                                    right = focusRequesters[1]
                                    down = focusRequesters[2]
                                }

                                1 -> {
                                    left = focusRequesters[0]
                                    down = focusRequesters[2]
                                }

                                else -> {
                                    left = focusRequesters[1]
                                    up = focusRequesters[0]
                                }
                            }
                        },
                )
            }
        }

        Text(
            text = state.status,
            modifier = Modifier.padding(top = 36.dp),
            color = Color(0xFF7DD3FC),
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun BootstrapButton(
    action: BootstrapAction,
    focusRequester: FocusRequester,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) Color(0xFF7DD3FC) else Color.Transparent

    Button(
        onClick = onActivate,
        modifier =
            modifier
                .widthIn(min = 180.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { isFocused = it.isFocused }
                .border(5.dp, borderColor, RoundedCornerShape(12.dp)),
    ) {
        Text(
            text = action.label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 22.sp,
        )
    }
}
