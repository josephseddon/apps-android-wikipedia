package org.wikipedia.feed.discover

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.dataclient.FeedClient
import org.wikipedia.feed.wikivoyage.WikivoyageArchiveRepository
import org.wikipedia.feed.wikivoyage.WikivoyageMainPageRepository
import org.wikipedia.util.log.L

class DiscoverClient(
    private val coroutineScope: CoroutineScope
) : FeedClient {

    private var clientJob: Job? = null

    override fun request(context: Context, wiki: WikiSite, age: Int, cb: FeedClient.Callback) {
        cancel()
        clientJob = coroutineScope.launch(
            CoroutineExceptionHandler { _, caught ->
                L.v(caught)
                cb.error(caught)
            }
        ) {
            val discover = if (age == 0) {
                WikivoyageMainPageRepository.getMainPageData(wiki).discover
            } else {
                WikivoyageArchiveRepository.getDiscoverForMonthsAgo(wiki, age)
            }
            cb.success(discover?.let { listOf(DiscoverCard(it, age, wiki)) }.orEmpty())
        }
    }

    override fun cancel() {
        clientJob?.cancel()
    }
}
