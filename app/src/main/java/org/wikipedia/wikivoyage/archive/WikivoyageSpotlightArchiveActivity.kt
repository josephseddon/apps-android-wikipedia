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

class WikivoyageSpotlightArchiveActivity : BaseActivity() {

    private val wiki by lazy { intent.parcelableExtra<WikiSite>(EXTRA_WIKI)!! }
    private val archivePageTitle by lazy { intent.getStringExtra(EXTRA_ARCHIVE_PAGE_TITLE)!! }
    private val screenTitle by lazy { intent.getStringExtra(EXTRA_SCREEN_TITLE)!! }

    private val viewModel: WikivoyageSpotlightArchiveViewModel by viewModels {
        WikivoyageSpotlightArchiveViewModel.Factory(wiki, archivePageTitle)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceUtil.setEdgeToEdge(this)

        setContent {
            BaseTheme {
                val uiState = viewModel.uiState.collectAsState().value
                WikivoyageSpotlightArchiveScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                    title = screenTitle,
                    uiState = uiState,
                    onBackButtonClick = { onBackPressedDispatcher.onBackPressed() },
                    onEntryClick = { entry ->
                        val pageTitle = PageTitle(entry.articleTitle, wiki)
                        val historyEntry = HistoryEntry(pageTitle, HistoryEntry.SOURCE_WIKIVOYAGE_ARCHIVE)
                        startActivity(PageActivity.newIntentForNewTab(this, historyEntry, pageTitle))
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
        private const val EXTRA_ARCHIVE_PAGE_TITLE = "archivePageTitle"
        private const val EXTRA_SCREEN_TITLE = "screenTitle"

        fun newIntent(context: Context, wiki: WikiSite, archivePageTitle: String, screenTitle: String): Intent {
            return Intent(context, WikivoyageSpotlightArchiveActivity::class.java)
                .putExtra(EXTRA_WIKI, wiki)
                .putExtra(EXTRA_ARCHIVE_PAGE_TITLE, archivePageTitle)
                .putExtra(EXTRA_SCREEN_TITLE, screenTitle)
        }
    }
}
