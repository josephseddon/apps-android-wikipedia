package org.wikipedia.wikivoyage.archive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.wikipedia.compose.components.HtmlText
import org.wikipedia.compose.components.WikiTopAppBar
import org.wikipedia.compose.components.error.WikiErrorClickEvents
import org.wikipedia.compose.components.error.WikiErrorView
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.feed.wikivoyage.WikivoyageDiscoverArchiveGroup
import org.wikipedia.theme.Theme
import org.wikipedia.util.UiState
import org.wikipedia.views.imageservice.ImageService

@Composable
fun WikivoyageDiscoverArchiveScreen(
    modifier: Modifier = Modifier,
    title: String,
    uiState: UiState<List<WikivoyageDiscoverArchiveGroup>>,
    onBackButtonClick: () -> Unit,
    onFactLinkClick: (url: String) -> Unit,
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
                        modifier = Modifier.align(Alignment.Center),
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
                val groups = uiState.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    itemsIndexed(groups) { index, group ->
                        if (index == 0 || groups[index - 1].heading != group.heading) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                text = group.heading,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = WikipediaTheme.colors.primaryColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                        WikivoyageDiscoverGroupRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            group = group,
                            onFactLinkClick = onFactLinkClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WikivoyageDiscoverGroupRow(
    modifier: Modifier = Modifier,
    group: WikivoyageDiscoverArchiveGroup,
    onFactLinkClick: (url: String) -> Unit
) {
    Row(modifier = modifier) {
        if (group.imageUrl != null) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageService.getRequest(context = context, url = group.imageUrl),
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                contentDescription = null,
                placeholder = ColorPainter(WikipediaTheme.colors.borderColor),
                error = ColorPainter(WikipediaTheme.colors.borderColor)
            )
            Spacer(modifier = Modifier.size(12.dp))
        }
        Column {
            group.facts.forEach { fact ->
                HtmlText(
                    modifier = Modifier.fillMaxWidth(),
                    text = "&bull; $fact",
                    linkInteractionListener = { linkAnnotation ->
                        (linkAnnotation as? androidx.compose.ui.text.LinkAnnotation.Url)?.url?.let { onFactLinkClick(it) }
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun WikivoyageDiscoverArchiveScreenPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        WikivoyageDiscoverArchiveScreen(
            modifier = Modifier.fillMaxSize(),
            title = "Discover",
            uiState = UiState.Success(
                listOf(
                    WikivoyageDiscoverArchiveGroup(
                        "July 2026",
                        listOf("<a href=\"/wiki/Franeker\">Franeker</a> has the world's oldest planetarium."),
                        null
                    )
                )
            ),
            onBackButtonClick = {},
            onFactLinkClick = {}
        )
    }
}
