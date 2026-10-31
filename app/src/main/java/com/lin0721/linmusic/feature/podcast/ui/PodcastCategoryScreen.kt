package com.lin0721.linmusic.feature.podcast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lin0721.linmusic.LocalBottomOverlayInset
import com.lin0721.linmusic.core.ui.components.SecondaryScreenScaffold
import com.lin0721.linmusic.core.ui.theme.rememberMelodiaGridColumns
import com.lin0721.linmusic.feature.home.ui.ErrorContent
import com.lin0721.linmusic.feature.home.ui.LoadingIndicator
import org.koin.androidx.compose.koinViewModel

// 分类二级页：该分类的热门电台（可翻页）与推荐节目
@Composable
fun PodcastCategoryScreen(
    categoryId: Long,
    name: String,
    onBack: () -> Unit,
    onRadioClick: (Long) -> Unit,
    viewModel: PodcastCategoryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(categoryId) { viewModel.load(categoryId) }

    SecondaryScreenScaffold(title = name, onBack = onBack, wideLayout = true) {
        when (val state = uiState) {
            PodcastCategoryUiState.Loading -> LoadingIndicator()
            is PodcastCategoryUiState.Error -> ErrorContent(message = state.message, onRetry = viewModel::retry)
            is PodcastCategoryUiState.Success -> CategoryContent(
                state = state,
                onRadioClick = onRadioClick,
                onLoadMore = viewModel::loadMore
            )
        }
    }
}

@Composable
private fun CategoryContent(
    state: PodcastCategoryUiState.Success,
    onRadioClick: (Long) -> Unit,
    onLoadMore: () -> Unit
) {
    val radioColumns = rememberMelodiaGridColumns(compact = 3, expandedPortrait = 5, expandedLandscape = 7)
    val listState = rememberLazyListState()
    LoadMoreEffect(listState, enabled = state.hasMore && !state.isLoadingMore, onLoadMore = onLoadMore)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalBottomOverlayInset.current + 16.dp)
    ) {
        if (state.radios.isEmpty()) {
            item(key = "empty") { SectionHint("这个分类暂时没有电台") }
            return@LazyColumn
        }
        item(key = "radios_title") { PodcastSectionTitle("热门电台") }
        podcastGridItems(state.radios, radioColumns, "radios") { radio, modifier ->
            PodcastRadioCard(radio = radio, onClick = { onRadioClick(radio.id) }, modifier = modifier)
        }
        if (state.isLoadingMore) {
            item(key = "loading_more") {
                Box(modifier = Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
