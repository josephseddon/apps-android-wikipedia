package org.wikipedia.wikivoyage.archive

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.wikipedia.R
import org.wikipedia.compose.components.error.WikiErrorClickEvents
import org.wikipedia.compose.components.error.WikiErrorView
import org.wikipedia.compose.components.WikiTopAppBar
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.feed.wikivoyage.WikivoyageArchiveEntry
import org.wikipedia.theme.Theme
import org.wikipedia.util.UiState
import org.wikipedia.views.imageservice.ImageService

@Composable
fun WikivoyageSpotlightArchiveScreen(
    modifier: Modifier = Modifier,
    title: String,
    uiState: UiState<List<WikivoyageArchiveEntry>>,
    onBackButtonClick: () -> Unit,
    onEntryClick: (WikivoyageArchiveEntry) -> Unit,
    wikiErrorClickEvents: WikiErrorClickEvents? = null
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            WikiTopAppBar(
                title = title,
                onNavigationClick = onBackButtonClick
            )
        },
        containerColor = WikipediaTheme.colors.paperColor
    ) { paddingValues ->
        when (uiState) {
            UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center),
                        color = WikipediaTheme.colors.progressiveColor
                    )
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    WikiErrorView(
                        modifier = Modifier.fillMaxWidth(),
                        caught = uiState.error,
                        errorClickEvents = wikiErrorClickEvents
                    )
                }
            }
            is UiState.Success -> {
                val entries = uiState.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    itemsIndexed(entries) { index, entry ->
                        if (index == 0 || entries[index - 1].year != entry.year) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                text = entry.year.toString(),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = WikipediaTheme.colors.primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        WikivoyageArchiveEntryRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEntryClick(entry) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            entry = entry
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikivoyageArchiveEntryRow(
    modifier: Modifier = Modifier,
    entry: WikivoyageArchiveEntry
) {
    val context = LocalContext.current
    Row(modifier = modifier) {
        AsyncImage(
            model = ImageService.getRequest(context = context, url = entry.imageUrl.orEmpty()),
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            contentDescription = null,
            placeholder = ColorPainter(WikipediaTheme.colors.borderColor),
            error = ColorPainter(WikipediaTheme.colors.borderColor)
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
        ) {
            Text(
                text = entry.month,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = WikipediaTheme.colors.secondaryColor
                )
            )
            Text(
                text = entry.articleTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = WikipediaTheme.colors.primaryColor,
                    fontWeight = FontWeight.Bold
                )
            )
            if (entry.caption.isNotEmpty()) {
                Text(
                    text = entry.caption,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = WikipediaTheme.colors.secondaryColor
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun WikivoyageSpotlightArchiveScreenPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        WikivoyageSpotlightArchiveScreen(
            modifier = Modifier.fillMaxSize(),
            title = stringResource(R.string.view_destination_of_the_month_card_title),
            uiState = UiState.Success(
                listOf(
                    WikivoyageArchiveEntry(2026, "June", "Galway", "Galway, Ireland", null),
                    WikivoyageArchiveEntry(2026, "May", "Cochabamba", "Cochabamba, Bolivia", null),
                    WikivoyageArchiveEntry(2025, "December", "Kowloon", "Hong Kong, China", null)
                )
            ),
            onBackButtonClick = {},
            onEntryClick = {}
        )
    }
}
