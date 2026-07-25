package org.wikipedia.feed.wikivoyage

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Looks up past Destination of the Month/Off the Beaten Path/Featured Travel Topic/Discover
 * picks from Wikivoyage's own archive pages, so scrolling the feed past the current month's
 * picks can keep going into previous months instead of stopping. The archive pages are always
 * authored in English regardless of the app's display language, so month names are matched in
 * English here rather than the device locale (see WikivoyageMainPageParser for the broader
 * caveat that this scraping only works reliably against English Wikivoyage).
 */
object WikivoyageArchiveRepository {
    private val SPOTLIGHT_ARCHIVE_PAGES = mapOf(
        WikivoyageSpotlightType.DESTINATION_OF_THE_MONTH to "Previous_Destinations_of_the_month",
        WikivoyageSpotlightType.OFF_THE_BEATEN_PATH to "Previously_Off_the_beaten_path",
        WikivoyageSpotlightType.FEATURED_TRAVEL_TOPIC to "Previous_Featured_travel_topics"
    )
    private const val DISCOVER_ARCHIVE_PAGE = "Discover"
    private val MONTH_NAME_FORMAT = SimpleDateFormat("MMMM", Locale.US)

    private val mutex = Mutex()
    private var cachedWiki: WikiSite? = null
    private var cachedSpotlightArchives: Map<WikivoyageSpotlightType, List<WikivoyageArchiveEntry>>? = null
    private var cachedDiscoverArchive: List<WikivoyageDiscoverArchiveGroup>? = null

    suspend fun getSpotlightForMonthsAgo(wiki: WikiSite, type: WikivoyageSpotlightType, monthsAgo: Int): WikivoyageSpotlight? {
        if (monthsAgo <= 0) return null
        ensureCached(wiki)
        val (monthName, year) = monthNameAndYearFor(monthsAgo)
        val entry = cachedSpotlightArchives?.get(type)?.find { it.year == year && it.month.equals(monthName, ignoreCase = true) } ?: return null
        val summary = runCatching { ServiceFactory.getRest(wiki).getPageSummary(entry.articleTitle) }.getOrNull()
        return WikivoyageSpotlight(
            type = type,
            articleTitle = entry.articleTitle,
            blurb = summary?.extract?.takeIf { it.isNotEmpty() } ?: entry.caption,
            imageUrl = summary?.thumbnailUrl ?: entry.imageUrl
        )
    }

    suspend fun getDiscoverForMonthsAgo(wiki: WikiSite, monthsAgo: Int): WikivoyageDiscover? {
        if (monthsAgo <= 0) return null
        ensureCached(wiki)
        val (monthName, year) = monthNameAndYearFor(monthsAgo)
        val heading = "$monthName $year"
        val group = cachedDiscoverArchive?.find { it.heading.equals(heading, ignoreCase = true) } ?: return null
        return WikivoyageDiscover(group.facts, group.imageUrl)
    }

    private fun monthNameAndYearFor(monthsAgo: Int): Pair<String, Int> {
        val calendar = Calendar.getInstance().apply { add(Calendar.MONTH, -monthsAgo) }
        return MONTH_NAME_FORMAT.format(calendar.time) to calendar.get(Calendar.YEAR)
    }

    private suspend fun ensureCached(wiki: WikiSite) {
        mutex.withLock {
            if (cachedWiki == wiki && cachedSpotlightArchives != null) {
                return
            }
            cachedWiki = wiki
            cachedSpotlightArchives = SPOTLIGHT_ARCHIVE_PAGES.mapValues { (_, pageTitle) ->
                runCatching {
                    WikivoyageArchiveParser.parseSpotlightArchive(ServiceFactory.get(wiki).parsePage(pageTitle).text)
                }.getOrDefault(emptyList())
            }
            cachedDiscoverArchive = runCatching {
                WikivoyageArchiveParser.parseDiscoverArchive(ServiceFactory.get(wiki).parsePage(DISCOVER_ARCHIVE_PAGE).text)
            }.getOrDefault(emptyList())
        }
    }
}
