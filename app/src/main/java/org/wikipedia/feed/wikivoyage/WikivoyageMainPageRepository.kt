package org.wikipedia.feed.wikivoyage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite

/**
 * Wikivoyage's Main Page carries the current Destination of the Month, Off the Beaten Path,
 * Featured Travel Topic, and Discover selections. There's no structured API for this content, so
 * it's scraped from the page's own rendered HTML. Since four separate feed cards all need the
 * same page, the parsed result is cached briefly so a single feed refresh only fetches it once.
 */
object WikivoyageMainPageRepository {
    private const val MAIN_PAGE_TITLE = "Main_Page"
    private val CACHE_TTL_MILLIS = java.util.concurrent.TimeUnit.MINUTES.toMillis(30)

    private val mutex = Mutex()
    private var cachedWiki: WikiSite? = null
    private var cachedData: WikivoyageMainPageData? = null
    private var cachedAtMillis: Long = 0

    suspend fun getMainPageData(wiki: WikiSite): WikivoyageMainPageData {
        mutex.withLock {
            val cached = cachedData
            if (cached != null && cachedWiki == wiki && System.currentTimeMillis() - cachedAtMillis < CACHE_TTL_MILLIS) {
                return cached
            }
            val html = ServiceFactory.get(wiki).parsePage(MAIN_PAGE_TITLE).text
            val parsed = WikivoyageMainPageParser.parse(html)
            cachedWiki = wiki
            cachedData = parsed
            cachedAtMillis = System.currentTimeMillis()
            return parsed
        }
    }
}
