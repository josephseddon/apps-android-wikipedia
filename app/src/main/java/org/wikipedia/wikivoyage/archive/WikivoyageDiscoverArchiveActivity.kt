package org.wikipedia.wikivoyage.archive

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import org.wikipedia.activity.BaseActivity
import org.wikipedia.compose.components.error.WikiErrorClickEvents
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.extensions.parcelableExtra
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.PageActivity
import org.wikipedia.page.PageTitle
import org.wikipedia.util.DeviceUtil
import org.wikipedia.util.UriUtil

class WikivoyageDiscoverArchiveActivity : BaseActivity() {

    private val wiki by lazy { intent.parcelableExtra<WikiSite>(EXTRA_WIKI)!! }
    private val screenTitle by lazy { intent.getStringExtra(EXTRA_SCREEN_TITLE)!! }

    private val viewModel: WikivoyageDiscoverArchiveViewModel by viewModels {
        WikivoyageDiscoverArchiveViewModel.Factory(wiki)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtil.setEdgeToEdge(this)

        setContent {
            BaseTheme {
                val uiState = viewModel.uiState.collectAsState().value
                WikivoyageDiscoverArchiveScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    title = screenTitle,
                    uiState = uiState,
                    onBackButtonClick = { onBackPressedDispatcher.onBackPressed() },
                    onFactLinkClick = { url ->
                        val articleTitle = UriUtil.getTitleFromUrl(url)
                        if (articleTitle.isNotEmpty()) {
                            val pageTitle = PageTitle(articleTitle, wiki)
                            val historyEntry = HistoryEntry(pageTitle, HistoryEntry.SOURCE_WIKIVOYAGE_ARCHIVE)
                            startActivity(PageActivity.newIntentForNewTab(this, historyEntry, pageTitle))
                        }
                    },
                    wikiErrorClickEvents = WikiErrorClickEvents(
                        backClickListener = { onBackPressedDispatcher.onBackPressed() },
                        retryClickListener = { viewModel.load() }
                    )
                )
            }
        }
    }

    companion object {
        private const val EXTRA_WIKI = "wiki"
        private const val EXTRA_SCREEN_TITLE = "screenTitle"

        fun newIntent(context: Context, wiki: WikiSite, screenTitle: String): Intent {
            return Intent(context, WikivoyageDiscoverArchiveActivity::class.java)
                .putExtra(EXTRA_WIKI, wiki)
                .putExtra(EXTRA_SCREEN_TITLE, screenTitle)
        }
    }
}
