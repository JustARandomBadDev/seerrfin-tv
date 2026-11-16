package io.github.justarandombaddev.seerrfintv.feature.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.justarandombaddev.seerrfintv.R
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinLibrary
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.PlaybackMethod

@Composable
fun JellyfinLibrariesScreen(
    state: JellyfinLibrariesUiState,
    onRetry: () -> Unit,
    onLibrarySelected: (String?) -> Unit,
    onItemActivated: (JellyfinCatalogItem) -> Unit,
    onSearch: () -> Unit,
    onItemFocused: (String) -> Unit,
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
        horizontalAlignment = Alignment.Start,
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
                            .fillMaxWidth()
                            .focusGroup(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item(key = "playback-resolution") {
                        Text(
                            text = playbackResolutionText(state),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = playbackResolutionColor(state.playbackStatus),
                            fontSize = 20.sp,
                        )
                    }
                    item(key = "library-filters") {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = onSearch,
                                modifier =
                                    if (state.restoreSearchFocus) {
                                        Modifier.focusRequester(initialFocusRequester)
                                    } else {
                                        Modifier
                                    },
                            ) {
                                Text(stringResource(R.string.home_search))
                            }
                            LibraryFilters(
                                libraries = state.libraries,
                                selectedLibraryId = state.selectedLibraryId,
                                initialFocusRequester = initialFocusRequester,
                                restoreItemFocus = state.focusedHomeItemId != null || state.restoreSearchFocus,
                                onLibrarySelected = onLibrarySelected,
                            )
                        }
                    }
                    items(
                        items = state.visibleHomeRows,
                        key = { row -> row.library.id },
                    ) { row ->
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = row.library.name,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().focusGroup(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp),
                            ) {
                                items(
                                    items = row.items,
                                    key = JellyfinCatalogItem::id,
                                ) { item ->
                                    Button(
                                        onClick = {
                                            catalogItemActivationRequest(state, item)?.let(onItemActivated)
                                        },
                                        modifier =
                                            Modifier
                                                .widthIn(min = 220.dp, max = 280.dp)
                                                .height(112.dp)
                                                .then(
                                                    if (item.id == state.focusedHomeItemId) {
                                                        Modifier.focusRequester(initialFocusRequester)
                                                    } else {
                                                        Modifier
                                                    },
                                                ).onFocusChanged { if (it.isFocused) onItemFocused(item.id) },
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(
                                                text = item.name,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 2,
                                            )
                                            Text(
                                                text = catalogItemMetadata(item),
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 16.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFilters(
    libraries: List<JellyfinLibrary>,
    selectedLibraryId: String?,
    initialFocusRequester: FocusRequester,
    restoreItemFocus: Boolean,
    onLibrarySelected: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "all") {
            Button(
                onClick = { onLibrarySelected(null) },
                modifier =
                    if (restoreItemFocus) Modifier else Modifier.focusRequester(initialFocusRequester),
            ) {
                Text(
                    text = stringResource(R.string.home_filter_all),
                    fontWeight = if (selectedLibraryId == null) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        items(libraries, key = JellyfinLibrary::id) { library ->
            Button(onClick = { onLibrarySelected(library.id) }) {
                Text(
                    text = library.name,
                    fontWeight = if (selectedLibraryId == library.id) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

internal fun catalogItemActivationRequest(
    state: JellyfinLibrariesUiState,
    item: JellyfinCatalogItem,
): JellyfinCatalogItem? =
    item.takeIf { candidate ->
        state.status == LibrariesStatus.Success &&
            state.playbackStatus != PlaybackResolutionStatus.Loading &&
            state.visibleHomeRows.any { row -> row.items.any { it.id == candidate.id } }
    }

private fun catalogItemMetadata(item: JellyfinCatalogItem): String {
    val type =
        when (item.type) {
            JellyfinCatalogItemType.Movie -> "Film"
            JellyfinCatalogItemType.Series -> "Série"
            JellyfinCatalogItemType.Episode -> "Épisode"
            JellyfinCatalogItemType.Video -> "Vidéo"
        }
    return item.productionYear?.let { year -> "$type · $year" } ?: type
}

@Composable
private fun playbackResolutionText(state: JellyfinLibrariesUiState): String =
    when (state.playbackStatus) {
        PlaybackResolutionStatus.Idle -> stringResource(R.string.libraries_playback_help)
        PlaybackResolutionStatus.Loading -> stringResource(R.string.libraries_playback_loading)
        PlaybackResolutionStatus.Ready -> {
            val source = state.playbackSource
            if (source == null) {
                stringResource(R.string.libraries_playback_error_unexpected)
            } else {
                stringResource(
                    R.string.libraries_playback_ready,
                    source.item.name,
                    playbackMethodText(source.method),
                )
            }
        }

        PlaybackResolutionStatus.Empty -> stringResource(R.string.libraries_playback_empty)
        PlaybackResolutionStatus.Error ->
            stringResource(
                R.string.libraries_playback_error,
                libraryErrorText(state.playbackError),
            )
    }

@Composable
private fun playbackMethodText(method: PlaybackMethod): String =
    when (method) {
        PlaybackMethod.DIRECT_PLAY -> stringResource(R.string.libraries_playback_method_direct)
        PlaybackMethod.DIRECT_STREAM -> stringResource(R.string.libraries_playback_method_stream)
        PlaybackMethod.TRANSCODE -> stringResource(R.string.libraries_playback_method_transcode)
    }

private fun playbackResolutionColor(status: PlaybackResolutionStatus): Color =
    when (status) {
        PlaybackResolutionStatus.Error -> Color(0xFFFCA5A5)
        PlaybackResolutionStatus.Ready -> Color(0xFF86EFAC)
        PlaybackResolutionStatus.Idle,
        PlaybackResolutionStatus.Loading,
        PlaybackResolutionStatus.Empty,
        -> Color(0xFF7DD3FC)
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
