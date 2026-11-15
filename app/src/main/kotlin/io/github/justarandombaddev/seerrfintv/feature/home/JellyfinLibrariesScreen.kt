package io.github.justarandombaddev.seerrfintv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R

@Composable
fun JellyfinLibrariesScreen(
    state: JellyfinLibrariesUiState,
    onRetry: () -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.status, state.libraries.firstOrNull()?.id) {
        if (
            state.status == LibrariesStatus.Success ||
            state.status == LibrariesStatus.Empty ||
            state.status == LibrariesStatus.Error
        ) {
            initialFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.libraries_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.libraries_subtitle),
            modifier = Modifier.padding(top = 12.dp),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )

        when (state.status) {
            LibrariesStatus.Restoring,
            LibrariesStatus.Loading,
            LibrariesStatus.NeedsServer,
            LibrariesStatus.NeedsLogin,
            -> {
                Text(
                    text = stringResource(R.string.libraries_loading),
                    modifier = Modifier.padding(top = 56.dp),
                    color = Color(0xFF7DD3FC),
                    fontSize = 22.sp,
                )
            }

            LibrariesStatus.Empty -> {
                LibraryMessageWithRetry(
                    message = stringResource(R.string.libraries_empty),
                    messageColor = Color(0xFFCBD5E1),
                    focusRequester = initialFocusRequester,
                    onRetry = onRetry,
                )
            }

            LibrariesStatus.Error -> {
                LibraryMessageWithRetry(
                    message = libraryErrorText(state.error),
                    messageColor = Color(0xFFFCA5A5),
                    focusRequester = initialFocusRequester,
                    onRetry = onRetry,
                )
            }

            LibrariesStatus.Success -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .padding(top = 32.dp)
                            .widthIn(max = 840.dp)
                            .fillMaxWidth()
                            .focusGroup(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(
                        items = state.libraries,
                        key = { _, library -> library.id },
                    ) { index, library ->
                        val focusModifier =
                            if (index == 0) {
                                Modifier.focusRequester(initialFocusRequester)
                            } else {
                                Modifier
                            }
                        Button(
                            onClick = {},
                            modifier = focusModifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = library.name,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                                fontSize = 24.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryMessageWithRetry(
    message: String,
    messageColor: Color,
    focusRequester: FocusRequester,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = message,
            color = messageColor,
            fontSize = 22.sp,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Text(
                text = stringResource(R.string.libraries_retry),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                fontSize = 22.sp,
            )
        }
    }
}

@Composable
private fun libraryErrorText(error: LibrariesUiError?): String =
    when (error) {
        LibrariesUiError.Unreachable -> stringResource(R.string.libraries_error_unreachable)
        LibrariesUiError.SecureConnection -> stringResource(R.string.libraries_error_tls)
        LibrariesUiError.ServerRejected -> stringResource(R.string.libraries_error_server)
        LibrariesUiError.InvalidResponse -> stringResource(R.string.libraries_error_response)
        LibrariesUiError.Unexpected,
        null,
        -> stringResource(R.string.libraries_error_unexpected)
    }
