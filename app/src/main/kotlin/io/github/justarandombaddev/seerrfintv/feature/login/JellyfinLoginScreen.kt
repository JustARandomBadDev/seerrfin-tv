package io.github.justarandombaddev.seerrfintv.feature.login

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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R

@Composable
fun JellyfinLoginScreen(
    state: JellyfinLoginUiState,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    if (state.isAuthenticated) {
        AuthenticatedContent(state.displayName)
        return
    }

    val usernameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        usernameFocusRequester.requestFocus()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.login_subtitle),
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )

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
            LoginTextField(
                value = state.username,
                hint = stringResource(R.string.login_username_hint),
                field = LoginField.Username,
                enabled = !state.isLoading,
                focusRequester = usernameFocusRequester,
                upFocusRequester = null,
                downFocusRequester = passwordFocusRequester,
                onValueChange = onUsernameChanged,
            )
            LoginTextField(
                value = state.password,
                hint = stringResource(R.string.login_password_hint),
                field = LoginField.Password,
                enabled = !state.isLoading,
                focusRequester = passwordFocusRequester,
                upFocusRequester = usernameFocusRequester,
                downFocusRequester = submitFocusRequester,
                onValueChange = onPasswordChanged,
                visualTransformation = PasswordVisualTransformation(),
                keyboardType = KeyboardType.Password,
            )
            Button(
                onClick = onLogin,
                enabled = !state.isLoading,
                modifier =
                    Modifier
                        .focusRequester(submitFocusRequester)
                        .focusProperties { up = passwordFocusRequester },
            ) {
                Text(
                    text =
                        if (state.isLoading) {
                            stringResource(R.string.login_loading)
                        } else {
                            stringResource(R.string.login_action)
                        },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    fontSize = 22.sp,
                )
            }
        }

        Text(
            text = loginStatusText(state),
            modifier = Modifier.padding(top = 26.dp),
            color = if (state.status == JellyfinLoginStatus.Error) Color(0xFFFCA5A5) else Color(0xFF7DD3FC),
            fontSize = 20.sp,
        )
    }
}

@Composable
private fun LoginTextField(
    value: String,
    hint: String,
    field: LoginField,
    enabled: Boolean,
    focusRequester: FocusRequester,
    upFocusRequester: FocusRequester?,
    downFocusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 22.sp),
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .focusProperties {
                    upFocusRequester?.let { up = it }
                    down = downFocusRequester
                }.onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    when (loginFieldKeyAction(field, event.key, event.type)) {
                        LoginFieldKeyAction.MoveToPassword,
                        LoginFieldKeyAction.MoveToSubmit,
                        -> {
                            keyboardController?.hide()
                            downFocusRequester.requestFocus()
                            true
                        }

                        LoginFieldKeyAction.ShowKeyboard -> {
                            keyboardController?.show()
                            true
                        }

                        LoginFieldKeyAction.None -> false
                    }
                }.background(Color(0xFF1E293B), shape)
                .border(
                    width = 5.dp,
                    color = if (isFocused) Color(0xFF7DD3FC) else Color(0xFF64748B),
                    shape = shape,
                ).padding(horizontal = 22.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(text = hint, color = Color(0xFF94A3B8), fontSize = 22.sp)
            }
            innerTextField()
        },
    )
}

@Composable
private fun AuthenticatedContent(displayName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.login_success_title),
            style = MaterialTheme.typography.displayMedium,
            color = Color(0xFF86EFAC),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.login_success_message, displayName),
            modifier = Modifier.padding(top = 20.dp),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
        )
    }
}

@Composable
private fun loginStatusText(state: JellyfinLoginUiState): String =
    when (state.status) {
        JellyfinLoginStatus.Idle -> stringResource(R.string.login_help)
        JellyfinLoginStatus.Loading -> stringResource(R.string.login_progress)
        JellyfinLoginStatus.Authenticated -> ""
        JellyfinLoginStatus.Error ->
            when (state.error) {
                JellyfinLoginUiError.EmptyUsername -> stringResource(R.string.login_error_username)
                JellyfinLoginUiError.InvalidCredentials -> stringResource(R.string.login_error_credentials)
                JellyfinLoginUiError.Unreachable -> stringResource(R.string.login_error_unreachable)
                JellyfinLoginUiError.SecureConnection -> stringResource(R.string.login_error_tls)
                JellyfinLoginUiError.ServerRejected -> stringResource(R.string.login_error_server)
                JellyfinLoginUiError.InvalidResponse -> stringResource(R.string.login_error_response)
                JellyfinLoginUiError.SessionStorage -> stringResource(R.string.login_error_storage)
                JellyfinLoginUiError.Unexpected,
                null,
                -> stringResource(R.string.login_error_unexpected)
            }
    }

internal enum class LoginField {
    Username,
    Password,
}

internal enum class LoginFieldKeyAction {
    MoveToPassword,
    MoveToSubmit,
    ShowKeyboard,
    None,
}

internal fun loginFieldKeyAction(
    field: LoginField,
    key: Key,
    type: KeyEventType,
): LoginFieldKeyAction {
    if (type != KeyEventType.KeyDown) return LoginFieldKeyAction.None
    return when (key) {
        Key.DirectionDown ->
            when (field) {
                LoginField.Username -> LoginFieldKeyAction.MoveToPassword
                LoginField.Password -> LoginFieldKeyAction.MoveToSubmit
            }

        Key.DirectionCenter,
        Key.Enter,
        -> LoginFieldKeyAction.ShowKeyboard

        else -> LoginFieldKeyAction.None
    }
}
