package io.github.justarandombaddev.seerrfintv.feature.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R

@Composable
fun ServerConnectionScreen(
    state: ServerConnectionUiState,
    onServerUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val inputFocusRequester = remember { FocusRequester() }
    val connectFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        inputFocusRequester.requestFocus()
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
            text = stringResource(R.string.server_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.server_subtitle),
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )

        Column(
            modifier =
                Modifier
                    .padding(top = 40.dp)
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ServerUrlField(
                value = state.serverUrl,
                enabled = !state.isLoading,
                focusRequester = inputFocusRequester,
                connectFocusRequester = connectFocusRequester,
                onValueChange = onServerUrlChanged,
            )

            Button(
                onClick = onConnect,
                enabled = !state.isLoading,
                modifier =
                    Modifier
                        .focusRequester(connectFocusRequester)
                        .focusProperties { up = inputFocusRequester },
            ) {
                Text(
                    text =
                        if (state.isLoading) {
                            stringResource(R.string.server_connecting)
                        } else {
                            stringResource(R.string.server_connect)
                        },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    fontSize = 22.sp,
                )
            }
        }

        Text(
            text = statusText(state),
            modifier = Modifier.padding(top = 32.dp),
            color = statusColor(state),
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun ServerUrlField(
    value: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    connectFocusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (isFocused) Color(0xFF7DD3FC) else Color(0xFF64748B)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 22.sp),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties { down = connectFocusRequester }
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    when (serverUrlKeyAction(event.key, event.type)) {
                        ServerUrlKeyAction.MoveToConnect -> {
                            keyboardController?.hide()
                            connectFocusRequester.requestFocus()
                            true
                        }

                        ServerUrlKeyAction.ShowKeyboard -> {
                            keyboardController?.show()
                            true
                        }

                        ServerUrlKeyAction.None -> false
                    }
                }.background(Color(0xFF1E293B), shape)
                .border(5.dp, borderColor, shape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.server_url_hint),
                    color = Color(0xFF94A3B8),
                    fontSize = 22.sp,
                )
            }
            innerTextField()
        },
    )
}

@Composable
private fun statusText(state: ServerConnectionUiState): String =
    when (state.status) {
        ServerConnectionStatus.Idle -> stringResource(R.string.server_help)
        ServerConnectionStatus.Loading -> stringResource(R.string.server_loading)
        ServerConnectionStatus.Connected -> stringResource(R.string.server_connected)
        ServerConnectionStatus.Error -> errorText(state.error)
    }

@Composable
private fun errorText(error: ServerConnectionUiError?): String =
    when (error) {
        ServerConnectionUiError.EmptyAddress -> stringResource(R.string.server_error_empty)
        ServerConnectionUiError.InvalidAddress -> stringResource(R.string.server_error_invalid)
        ServerConnectionUiError.UnsupportedScheme -> stringResource(R.string.server_error_scheme)
        ServerConnectionUiError.CredentialsNotAllowed -> stringResource(R.string.server_error_credentials)
        ServerConnectionUiError.QueryOrFragmentNotAllowed -> stringResource(R.string.server_error_query)
        ServerConnectionUiError.Unreachable -> stringResource(R.string.server_error_unreachable)
        ServerConnectionUiError.SecureConnection -> stringResource(R.string.server_error_tls)
        ServerConnectionUiError.ServerRejected -> stringResource(R.string.server_error_http)
        ServerConnectionUiError.InvalidResponse -> stringResource(R.string.server_error_response)
        ServerConnectionUiError.SettingsFailure -> stringResource(R.string.server_error_settings)
        ServerConnectionUiError.Unexpected,
        null,
        -> stringResource(R.string.server_error_unexpected)
    }

private fun statusColor(state: ServerConnectionUiState): Color =
    when (state.status) {
        ServerConnectionStatus.Error -> Color(0xFFFCA5A5)
        ServerConnectionStatus.Connected -> Color(0xFF86EFAC)
        else -> Color(0xFF7DD3FC)
    }

internal enum class ServerUrlKeyAction {
    MoveToConnect,
    ShowKeyboard,
    None,
}

internal fun serverUrlKeyAction(
    key: Key,
    type: KeyEventType,
): ServerUrlKeyAction {
    if (type != KeyEventType.KeyDown) return ServerUrlKeyAction.None
    return when (key) {
        Key.DirectionDown -> ServerUrlKeyAction.MoveToConnect
        Key.DirectionCenter,
        Key.Enter,
        -> ServerUrlKeyAction.ShowKeyboard

        else -> ServerUrlKeyAction.None
    }
}
