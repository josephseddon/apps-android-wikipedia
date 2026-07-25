package org.wikipedia.feed.featuredtraveltopic

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.dataclient.FeedClient
import org.wikipedia.feed.wikivoyage.WikivoyageMainPageRepository
import org.wikipedia.feed.wikivoyage.WikivoyageSpotlightType
import org.wikipedia.util.log.L

class FeaturedTravelTopicClient(
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
            val spotlight = WikivoyageMainPageRepository.getMainPageData(wiki)
                .spotlights[WikivoyageSpotlightType.FEATURED_TRAVEL_TOPIC]
            cb.success(spotlight?.let { listOf(FeaturedTravelTopicCard(it, age, wiki)) }.orEmpty())
        }
    }

    override fun cancel() {
        clientJob?.cancel()
    }
}
