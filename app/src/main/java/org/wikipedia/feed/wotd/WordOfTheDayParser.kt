package org.wikipedia.feed.wotd

import org.jsoup.Jsoup
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.page.PageSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Wiktionary has no WikiFeeds deployment, so `feed/featured/...` (the endpoint the "Featured
 * article" card relies on elsewhere in the app) never has content there. Wiktionary maintains
 * its own community-curated "Word of the day" wiki page instead, at a per-day title such as
 * `Wiktionary:Word_of_the_day/2026/July_24`, using stable `WOTD-rss-*` element IDs that the page
 * itself is designed around for programmatic (RSS) extraction. This fetches and parses that page
 * and adapts it into a [PageSummary], so it can be shown as a feed card the same way as any other
 * page-based card.
 */
object WordOfTheDayParser {

    suspend fun fetch(wiki: WikiSite, age: Int): PageSummary? {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { add(Calendar.DATE, -age) }
        val monthDayFormat = SimpleDateFormat("MMMM_d", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val pageTitle = "Wiktionary:Word_of_the_day/${calendar[Calendar.YEAR]}/${monthDayFormat.format(calendar.time)}"

        val html = ServiceFactory.get(wiki).parsePage(pageTitle).text
        if (html.isEmpty()) {
            return null
        }

        val doc = Jsoup.parse(html)
        val word = doc.selectFirst("#$ID_TITLE")?.text().orEmpty().trim()
        val definitions = doc.selectFirst("#$ID_DESCRIPTION")?.select("li")
            ?.map { it.text().trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (word.isEmpty() || definitions.isEmpty()) {
            return null
        }

        return PageSummary(
            titles = PageSummary.Titles(word, word),
            lang = wiki.languageCode,
            extract = definitions.joinToString("\n") { "• $it" },
            description = definitions.first()
        )
    }

    private const val ID_TITLE = "WOTD-rss-title"
    private const val ID_DESCRIPTION = "WOTD-rss-description"
}
