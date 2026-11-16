package io.github.justarandombaddev.seerrfintv.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.feature.home.JellyfinSearchUiState
import io.github.justarandombaddev.seerrfintv.feature.home.LibrariesUiError
import io.github.justarandombaddev.seerrfintv.feature.home.SearchStatus

@Composable
fun JellyfinSearchScreen(
    state: JellyfinSearchUiState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onResultFocused: (String) -> Unit,
    onResultActivated: (JellyfinCatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val inputFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    val resultFocusRequester = remember(state.results, state.focusedItemId) { FocusRequester() }

    LaunchedEffect(state.status, state.results, state.focusedItemId) {
        if (state.status == SearchStatus.Results && state.results.isNotEmpty()) {
            resultFocusRequester.requestFocus()
        } else if (state.status != SearchStatus.Loading) {
            inputFocusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.search_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.search_subtitle),
            color = Color(0xFFCBD5E1),
            fontSize = 22.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SearchTextField(
                value = state.query,
                enabled = state.status != SearchStatus.Loading,
                inputFocusRequester = inputFocusRequester,
                submitFocusRequester = submitFocusRequester,
                onValueChange = onQueryChanged,
                onSubmit = onSubmit,
            )
            Button(
                onClick = onSubmit,
                enabled = state.query.isNotBlank() && state.status != SearchStatus.Loading,
                modifier = Modifier.focusRequester(submitFocusRequester).focusProperties { left = inputFocusRequester },
            ) {
                Text(text = stringResource(R.string.search_action), fontSize = 21.sp)
            }
        }

        when (state.status) {
            SearchStatus.Editing -> Text(stringResource(R.string.search_help), color = Color(0xFF7DD3FC), fontSize = 20.sp)
            SearchStatus.Loading -> Text(stringResource(R.string.search_loading), color = Color(0xFF7DD3FC), fontSize = 22.sp)
            SearchStatus.Empty -> Text(stringResource(R.string.search_empty), color = Color(0xFFCBD5E1), fontSize = 22.sp)
            SearchStatus.Error -> Text(searchErrorText(state.error), color = Color(0xFFFCA5A5), fontSize = 22.sp)
            SearchStatus.Results ->
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.results, key = JellyfinCatalogItem::id) { item ->
                        val restoresFocus = item.id == (state.focusedItemId ?: state.results.first().id)
                        Button(
                            onClick = { onResultActivated(item) },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .widthIn(max = 900.dp)
                                    .height(96.dp)
                                    .then(if (restoresFocus) Modifier.focusRequester(resultFocusRequester) else Modifier)
                                    .onFocusChanged { if (it.isFocused) onResultFocused(item.id) },
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(item.name, fontSize = 21.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(searchMetadata(item), color = Color(0xFFCBD5E1), fontSize = 16.sp)
                            }
                        }
                    }
                }
        }
    }
}

@Composable
private fun SearchTextField(
    value: String,
    enabled: Boolean,
    inputFocusRequester: FocusRequester,
    submitFocusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide(); onSubmit() }),
        textStyle = TextStyle(color = Color(0xFFF8FAFC), fontSize = 22.sp),
        modifier =
            Modifier
                .fillMaxWidth(0.75f)
                .focusRequester(inputFocusRequester)
                .focusProperties { right = submitFocusRequester; down = submitFocusRequester }
                .onFocusChanged { isFocused = it.isFocused }
                .onPreviewKeyEvent { event ->
                    when (searchFieldKeyAction(event.key, event.type)) {
                        SearchFieldKeyAction.MoveToSubmit -> {
                            keyboardController?.hide()
                            submitFocusRequester.requestFocus()
                            true
                        }
                        SearchFieldKeyAction.ShowKeyboard -> {
                            keyboardController?.show()
                            true
                        }
                        SearchFieldKeyAction.None -> false
                    }
                }
                .background(Color(0xFF1E293B), shape)
                .border(5.dp, if (isFocused) Color(0xFF7DD3FC) else Color(0xFF64748B), shape)
                .padding(horizontal = 22.dp, vertical = 16.dp),
        decorationBox = { innerTextField ->
            if (value.isEmpty()) Text(stringResource(R.string.search_hint), color = Color(0xFF94A3B8), fontSize = 22.sp)
            innerTextField()
        },
    )
}

private fun searchMetadata(item: JellyfinCatalogItem): String {
    val type =
        when (item.type) {
            JellyfinCatalogItemType.Movie -> "Film"
            JellyfinCatalogItemType.Series -> "Série"
            JellyfinCatalogItemType.Episode -> "Épisode"
            JellyfinCatalogItemType.Video -> "Vidéo"
        }
    return item.productionYear?.let { "$type · $it" } ?: type
}

internal enum class SearchFieldKeyAction {
    MoveToSubmit,
    ShowKeyboard,
    None,
}

internal fun searchFieldKeyAction(key: Key, type: KeyEventType): SearchFieldKeyAction {
    if (type != KeyEventType.KeyDown) return SearchFieldKeyAction.None
    return when (key) {
        Key.DirectionDown -> SearchFieldKeyAction.MoveToSubmit
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> SearchFieldKeyAction.ShowKeyboard
        else -> SearchFieldKeyAction.None
    }
}

@Composable
private fun searchErrorText(error: LibrariesUiError?): String =
    when (error) {
        LibrariesUiError.Unreachable -> stringResource(R.string.libraries_error_unreachable)
        LibrariesUiError.SecureConnection -> stringResource(R.string.libraries_error_tls)
        LibrariesUiError.ServerRejected -> stringResource(R.string.libraries_error_server)
        LibrariesUiError.InvalidResponse -> stringResource(R.string.libraries_error_response)
        LibrariesUiError.Unexpected, null -> stringResource(R.string.libraries_error_unexpected)
    }
