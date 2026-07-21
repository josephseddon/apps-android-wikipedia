package org.wikipedia.games.wikifamous

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import org.wikipedia.Constants
import org.wikipedia.activity.BaseActivity
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.PageActivity
import org.wikipedia.page.PageTitle

class WikiFamousGameActivity : BaseActivity() {

    private val viewModel: WikiFamousGameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BaseTheme {
                WikiFamousGameScreen(
                    viewModel = viewModel,
                    onBackClick = { finish() },
                    onReadArticle = { title -> openArticle(title) }
                )
            }
        }
    }

    private fun openArticle(title: String) {
        val pageTitle = PageTitle(title, viewModel.wikiSite)
        val entry = HistoryEntry(pageTitle, HistoryEntry.SOURCE_WIKI_FAMOUS_GAME)
        startActivity(PageActivity.newIntentForNewTab(this, entry, pageTitle))
    }

    companion object {
        fun newIntent(context: Context, invokeSource: Constants.InvokeSource, wikiSite: WikiSite): Intent {
            return Intent(context, WikiFamousGameActivity::class.java)
                .putExtra(Constants.ARG_WIKISITE, wikiSite)
                .putExtra(Constants.INTENT_EXTRA_INVOKE_SOURCE, invokeSource)
        }
    }
}
