package io.github.justarandombaddev.seerrfintv.feature.seerr

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R

@Composable
fun SeerrAuthScreen(
    state: SeerrAuthUiState,
    onServerUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val primaryFocusRequester = remember { FocusRequester() }
    val connectFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.status) {
        primaryFocusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.seerr_auth_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.seerr_auth_subtitle),
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )

        when (state.status) {
            SeerrAuthStatus.Editing,
            SeerrAuthStatus.Error,
            ->
                SeerrServerEditor(
                    state = state,
                    urlFocusRequester = primaryFocusRequester,
                    connectFocusRequester = connectFocusRequester,
                    onServerUrlChanged = onServerUrlChanged,
                    onConnect = onConnect,
                )

            SeerrAuthStatus.WaitingForAuthorization ->
                WaitingContent(state, primaryFocusRequester, onBack)

            SeerrAuthStatus.Authenticated ->
                AuthenticatedContent(state, primaryFocusRequester, onBack)

            SeerrAuthStatus.Restoring,
            SeerrAuthStatus.Initiating,
            SeerrAuthStatus.Authenticating,
            -> ProgressContent(state.status, primaryFocusRequester, onBack)
        }
    }
}

@Composable
private fun SeerrServerEditor(
    state: SeerrAuthUiState,
    urlFocusRequester: FocusRequester,
    connectFocusRequester: FocusRequester,
    onServerUrlChanged: (String) -> Unit,
    onConnect: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .padding(top = 32.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        var isFocused by remember { mutableStateOf(false) }
        val keyboardController = LocalSoftwareKeyboardController.current
        val shape = RoundedCornerShape(12.dp)
        BasicTextField(
            value = state.serverUrl,
            onValueChange = onServerUrlChanged,
            enabled = state.canEditServerUrl,
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 22.sp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(urlFocusRequester)
                    .focusProperties { down = connectFocusRequester }
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        when (seerrUrlKeyAction(event.key, event.type)) {
                            SeerrUrlKeyAction.MoveToConnect -> {
                                keyboardController?.hide()
                                connectFocusRequester.requestFocus()
                                true
                            }

                            SeerrUrlKeyAction.ShowKeyboard -> {
                                keyboardController?.show()
                                true
                            }

                            SeerrUrlKeyAction.None -> false
                        }
                    }.background(Color(0xFF1E293B), shape)
                    .border(
                        width = 5.dp,
                        color = if (isFocused) Color(0xFF7DD3FC) else Color(0xFF64748B),
                        shape = shape,
                    ).padding(horizontal = 22.dp, vertical = 16.dp),
            decorationBox = { innerTextField ->
                if (state.serverUrl.isEmpty()) {
                    Text(
                        text = stringResource(R.string.seerr_auth_url_hint),
                        color = Color(0xFF94A3B8),
                        fontSize = 22.sp,
                    )
                }
                innerTextField()
            },
        )
        Button(
            onClick = onConnect,
            modifier =
                Modifier
                    .focusRequester(connectFocusRequester)
                    .focusProperties { up = urlFocusRequester },
        ) {
            Text(
                text = stringResource(R.string.seerr_auth_connect),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 22.sp,
            )
        }
        Text(
            text = state.error?.let { seerrErrorText(it) } ?: stringResource(R.string.seerr_auth_help),
            color = if (state.error == null) Color(0xFF7DD3FC) else Color(0xFFFCA5A5),
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun WaitingContent(
    state: SeerrAuthUiState,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    Text(
        text = requireNotNull(state.code),
        modifier = Modifier.padding(top = 36.dp),
        color = Color(0xFF7DD3FC),
        fontSize = 52.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.seerr_auth_quick_connect_help),
        modifier = Modifier.padding(top = 18.dp).widthIn(max = 800.dp),
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 22.sp,
    )
    Button(
        onClick = onBack,
        modifier = Modifier.padding(top = 28.dp).focusRequester(focusRequester),
    ) {
        Text(stringResource(R.string.seerr_auth_cancel))
    }
}

@Composable
private fun AuthenticatedContent(
    state: SeerrAuthUiState,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val user = requireNotNull(state.user)
    Text(
        text = stringResource(R.string.seerr_auth_connected, user.displayName),
        modifier = Modifier.padding(top = 30.dp),
        color = Color(0xFF86EFAC),
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
    )
    Column(
        modifier = Modifier.padding(top = 20.dp).widthIn(max = 680.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PermissionLine(R.string.seerr_permission_movies, user.permissions.canRequestMovies)
        PermissionLine(R.string.seerr_permission_series, user.permissions.canRequestSeries)
        PermissionLine(R.string.seerr_permission_4k_movies, user.permissions.canRequest4kMovies)
        PermissionLine(R.string.seerr_permission_4k_series, user.permissions.canRequest4kSeries)
    }
    Button(
        onClick = onBack,
        modifier = Modifier.padding(top = 28.dp).focusRequester(focusRequester),
    ) {
        Text(stringResource(R.string.seerr_auth_back))
    }
}

@Composable
private fun PermissionLine(
    labelResource: Int,
    allowed: Boolean,
) {
    Text(
        text =
            stringResource(
                R.string.seerr_permission_line,
                stringResource(labelResource),
                stringResource(if (allowed) R.string.seerr_permission_allowed else R.string.seerr_permission_denied),
            ),
        color = if (allowed) Color(0xFF86EFAC) else Color(0xFFFCA5A5),
        fontSize = 21.sp,
    )
}

@Composable
private fun ProgressContent(
    status: SeerrAuthStatus,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val message =
        when (status) {
            SeerrAuthStatus.Restoring -> R.string.seerr_auth_restoring
            SeerrAuthStatus.Initiating -> R.string.seerr_auth_initiating
            SeerrAuthStatus.Authenticating -> R.string.seerr_auth_authenticating
            else -> error("Not a progress status")
        }
    Text(
        text = stringResource(message),
        modifier = Modifier.padding(top = 36.dp),
        color = Color(0xFF7DD3FC),
        fontSize = 22.sp,
    )
    Button(
        onClick = onBack,
        modifier = Modifier.padding(top = 28.dp).focusRequester(focusRequester),
    ) {
        Text(stringResource(R.string.seerr_auth_cancel))
    }
}

@Composable
private fun seerrErrorText(error: SeerrAuthError): String =
    stringResource(
        when (error) {
            SeerrAuthError.InvalidServerAddress -> R.string.seerr_error_address
            SeerrAuthError.QuickConnectUnavailable -> R.string.seerr_error_quick_connect_unavailable
            SeerrAuthError.QuickConnectExpired -> R.string.seerr_error_quick_connect_expired
            SeerrAuthError.QuickConnectTimedOut -> R.string.seerr_error_quick_connect_timeout
            SeerrAuthError.AccessDenied -> R.string.seerr_error_access_denied
            SeerrAuthError.SessionExpired -> R.string.seerr_error_session_expired
            SeerrAuthError.SessionUnreadable -> R.string.seerr_error_session_unreadable
            SeerrAuthError.Unreachable -> R.string.seerr_error_unreachable
            SeerrAuthError.SecureConnection -> R.string.seerr_error_tls
            SeerrAuthError.ServerRejected -> R.string.seerr_error_server
            SeerrAuthError.InvalidResponse -> R.string.seerr_error_response
            SeerrAuthError.SessionStorage -> R.string.seerr_error_storage
            SeerrAuthError.Unexpected -> R.string.seerr_error_unexpected
        },
    )

internal enum class SeerrUrlKeyAction {
    MoveToConnect,
    ShowKeyboard,
    None,
}

internal fun seerrUrlKeyAction(
    key: Key,
    type: KeyEventType,
): SeerrUrlKeyAction {
    if (type != KeyEventType.KeyDown) return SeerrUrlKeyAction.None
    return when (key) {
        Key.DirectionDown -> SeerrUrlKeyAction.MoveToConnect
        Key.DirectionCenter,
        Key.Enter,
        -> SeerrUrlKeyAction.ShowKeyboard
        else -> SeerrUrlKeyAction.None
    }
}
