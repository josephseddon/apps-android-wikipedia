package org.wikipedia.feed.offthebeatenpath

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.dataclient.FeedClient
import org.wikipedia.feed.wikivoyage.WikivoyageArchiveRepository
import org.wikipedia.feed.wikivoyage.WikivoyageMainPageRepository
import org.wikipedia.feed.wikivoyage.WikivoyageSpotlightType
import org.wikipedia.util.log.L

class OffTheBeatenPathClient(
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
            val spotlight = if (age == 0) {
                WikivoyageMainPageRepository.getMainPageData(wiki).spotlights[WikivoyageSpotlightType.OFF_THE_BEATEN_PATH]
            } else {
                WikivoyageArchiveRepository.getSpotlightForMonthsAgo(wiki, WikivoyageSpotlightType.OFF_THE_BEATEN_PATH, age)
            }
            cb.success(spotlight?.let { listOf(OffTheBeatenPathCard(it, age, wiki)) }.orEmpty())
        }
    }

    override fun cancel() {
        clientJob?.cancel()
    }
}
