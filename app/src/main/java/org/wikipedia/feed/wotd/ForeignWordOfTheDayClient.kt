package org.wikipedia.feed.wotd

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import org.wikipedia.WikipediaApp
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.FeedContentType
import org.wikipedia.feed.dataclient.FeedClient
import org.wikipedia.util.log.L

class ForeignWordOfTheDayClient(
    private val coroutineScope: CoroutineScope
) : FeedClient {

    private var clientJob: Job? = null

    override fun request(context: Context, wiki: WikiSite, age: Int, cb: FeedClient.Callback) {
        cancel()
        clientJob = coroutineScope.launch(
            CoroutineExceptionHandler { _, caught ->
                L.v(caught)
                cb.success(emptyList())
            }
        ) {
            val deferredCards = WikipediaApp.instance.languageState.appLanguageCodes
                .filter { !FeedContentType.FOREIGN_WORD_OF_THE_DAY.langCodesDisabled.contains(it) }
                .map { langCode ->
                    async {
                        val wikiSite = WikiSite.forLanguageCode(langCode)
                        try {
                            WordOfTheDayParser.fetchForeign(wikiSite, age)?.let { ForeignWordOfTheDayCard(it, age, wikiSite) }
                        } catch (e: Exception) {
                            L.v(e)
                            null
                        }
                    }
                }
            cb.success(deferredCards.awaitAll().filterNotNull())
        }
    }

    override fun cancel() {
        clientJob?.cancel()
    }
}
