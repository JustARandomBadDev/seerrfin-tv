package io.github.justarandombaddev.seerrfintv.feature.details

import androidx.activity.compose.BackHandler
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
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItem
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinCatalogItemType
import io.github.justarandombaddev.seerrfintv.domain.model.JellyfinSeason
import io.github.justarandombaddev.seerrfintv.feature.home.JellyfinMediaDetailsUiState
import io.github.justarandombaddev.seerrfintv.feature.home.MediaDetailsStatus
import io.github.justarandombaddev.seerrfintv.feature.home.PlaybackResolutionStatus

@Composable
fun JellyfinMediaDetailsScreen(
    state: JellyfinMediaDetailsUiState,
    playbackStatus: PlaybackResolutionStatus,
    onSeasonSelected: (String) -> Unit,
    onPlay: (JellyfinCatalogItem) -> Unit,
    onBack: () -> Unit,
) {
    val initialFocusRequester = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    LaunchedEffect(state.status, state.item?.id, state.selectedSeasonId) {
        if (state.status != MediaDetailsStatus.Loading) initialFocusRequester.requestFocus()
    }

    val item = state.item ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp).focusGroup(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item(key = "title") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = detailMetadata(item),
                    color = Color(0xFFCBD5E1),
                    fontSize = 22.sp,
                )
            }
        }

        when (state.status) {
            MediaDetailsStatus.Loading ->
                item(key = "loading") {
                    Text(
                        text = stringResource(R.string.details_loading),
                        color = Color(0xFF7DD3FC),
                        fontSize = 22.sp,
                    )
                }

            MediaDetailsStatus.Empty ->
                item(key = "empty") {
                    DetailMessageWithBack(
                        message = stringResource(R.string.details_empty),
                        focusRequester = initialFocusRequester,
                        onBack = onBack,
                    )
                }

            MediaDetailsStatus.Error ->
                item(key = "error") {
                    DetailMessageWithBack(
                        message = stringResource(R.string.details_error),
                        focusRequester = initialFocusRequester,
                        onBack = onBack,
                    )
                }

            MediaDetailsStatus.Ready -> {
                if (item.type == JellyfinCatalogItemType.Series) {
                    item(key = "seasons-title") {
                        Text(
                            text = stringResource(R.string.details_seasons),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    item(key = "seasons") {
                        SeasonRow(
                            seasons = state.seasons,
                            selectedSeasonId = state.selectedSeasonId,
                            initialFocusRequester = initialFocusRequester,
                            onSeasonSelected = onSeasonSelected,
                        )
                    }
                    item(key = "episodes-title") {
                        Text(
                            text = stringResource(R.string.details_episodes),
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (state.episodes.isEmpty()) {
                        item(key = "episodes-empty") {
                            Text(
                                text = stringResource(R.string.details_episodes_empty),
                                color = Color(0xFFCBD5E1),
                                fontSize = 20.sp,
                            )
                        }
                    } else {
                        item(key = "episodes") {
                            EpisodeRow(state, playbackStatus, onPlay)
                        }
                    }
                } else {
                    item(key = "play") {
                        Button(
                            onClick = { detailPlaybackRequest(state, item)?.let(onPlay) },
                            enabled = playbackStatus != PlaybackResolutionStatus.Loading,
                            modifier = Modifier.focusRequester(initialFocusRequester),
                        ) {
                            Text(
                                text =
                                    if (item.playbackPositionTicks > 0) {
                                        stringResource(R.string.details_resume)
                                    } else {
                                        stringResource(R.string.details_play)
                                    },
                                fontSize = 22.sp,
                            )
                        }
                    }
                }
            }

            MediaDetailsStatus.Closed -> Unit
        }

        if (playbackStatus == PlaybackResolutionStatus.Loading) {
            item(key = "playback-loading") {
                Text(
                    text = stringResource(R.string.libraries_playback_loading),
                    color = Color(0xFF7DD3FC),
                    fontSize = 20.sp,
                )
            }
        } else if (playbackStatus == PlaybackResolutionStatus.Empty || playbackStatus == PlaybackResolutionStatus.Error) {
            item(key = "playback-error") {
                Text(
                    text = stringResource(R.string.details_playback_error),
                    color = Color(0xFFFCA5A5),
                    fontSize = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun SeasonRow(
    seasons: List<JellyfinSeason>,
    selectedSeasonId: String?,
    initialFocusRequester: FocusRequester,
    onSeasonSelected: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(seasons, key = JellyfinSeason::id) { season ->
            Button(
                onClick = { onSeasonSelected(season.id) },
                modifier =
                    if (season.id == selectedSeasonId) Modifier.focusRequester(initialFocusRequester)
                    else Modifier,
            ) {
                Text(
                    text = season.name,
                    fontWeight = if (season.id == selectedSeasonId) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    state: JellyfinMediaDetailsUiState,
    playbackStatus: PlaybackResolutionStatus,
    onPlay: (JellyfinCatalogItem) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items(state.episodes, key = JellyfinCatalogItem::id) { episode ->
            Button(
                onClick = { detailPlaybackRequest(state, episode)?.let(onPlay) },
                enabled = playbackStatus != PlaybackResolutionStatus.Loading,
                modifier = Modifier.widthIn(min = 240.dp, max = 320.dp).height(112.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = episode.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                    )
                    Text(
                        text = episodeMetadata(episode),
                        color = Color(0xFFCBD5E1),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailMessageWithBack(
    message: String,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(text = message, color = Color(0xFFFCA5A5), fontSize = 22.sp)
        Button(onClick = onBack, modifier = Modifier.focusRequester(focusRequester)) {
            Text(text = stringResource(R.string.details_back))
        }
    }
}

internal fun detailPlaybackRequest(
    state: JellyfinMediaDetailsUiState,
    item: JellyfinCatalogItem,
): JellyfinCatalogItem? =
    item.takeIf { candidate ->
        state.status == MediaDetailsStatus.Ready &&
            when (candidate.type) {
                JellyfinCatalogItemType.Movie,
                JellyfinCatalogItemType.Video,
                -> state.item?.let { it.id == candidate.id && it.type == candidate.type } == true

                JellyfinCatalogItemType.Episode ->
                    state.episodes.any { it.id == candidate.id && it.type == JellyfinCatalogItemType.Episode }

                JellyfinCatalogItemType.Series -> false
            }
    }

private fun detailMetadata(item: JellyfinCatalogItem): String =
    when (item.type) {
        JellyfinCatalogItemType.Movie -> item.productionYear?.let { "Film · $it" } ?: "Film"
        JellyfinCatalogItemType.Series -> item.productionYear?.let { "Série · $it" } ?: "Série"
        JellyfinCatalogItemType.Video -> item.productionYear?.let { "Vidéo · $it" } ?: "Vidéo"
        JellyfinCatalogItemType.Episode -> episodeMetadata(item)
    }

private fun episodeMetadata(item: JellyfinCatalogItem): String {
    val number =
        listOfNotNull(
            item.parentIndexNumber?.let { "S$it" },
            item.indexNumber?.let { "E$it" },
        ).joinToString(" · ")
    return if (item.playbackPositionTicks > 0) {
        listOf(number, "À reprendre").filter(String::isNotBlank).joinToString(" · ")
    } else {
        number.ifBlank { "Épisode" }
    }
}
