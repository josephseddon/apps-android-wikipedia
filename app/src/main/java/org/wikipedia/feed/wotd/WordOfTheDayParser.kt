package org.wikipedia.feed.wotd

import org.jsoup.nodes.Document
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
 * article" card relies on elsewhere in the app) never has content there. Wiktionary maintains its
 * own community-curated "Word of the day" and "Foreign word of the day" wiki pages instead, at
 * per-day titles such as `Wiktionary:Word_of_the_day/2026/July_24` and
 * `Wiktionary:Foreign_Word_of_the_Day/2026/July_25`, using stable `WOTD-rss-*`/`FWOTD-rss-*`
 * element IDs that the pages themselves are designed around for programmatic (RSS) extraction.
 * This fetches and parses those pages and adapts them into [PageSummary]s, so they can be shown
 * as feed cards the same way as any other page-based card.
 */
object WordOfTheDayParser {

    suspend fun fetch(wiki: WikiSite, age: Int): PageSummary? {
        val doc = fetchDoc(wiki, "Wiktionary:Word_of_the_day", age) ?: return null

        val word = doc.selectFirst("#WOTD-rss-title")?.text().orEmpty().trim()
        val definitions = doc.selectFirst("#WOTD-rss-description")?.select("li")
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

    suspend fun fetchForeign(wiki: WikiSite, age: Int): PageSummary? {
        val doc = fetchDoc(wiki, "Wiktionary:Foreign_Word_of_the_Day", age) ?: return null

        val word = doc.selectFirst(".headword-line strong.headword a")?.text().orEmpty().trim()
        val transliteration = doc.selectFirst(".headword-tr")?.text()?.trim()
        val language = doc.selectFirst("#FWOTD-rss-language")?.text().orEmpty().trim()
        val definitions = doc.selectFirst("#FWOTD-rss-description")?.select("li")
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
            description = if (language.isEmpty()) {
                transliteration
            } else if (transliteration.isNullOrEmpty() || transliteration == word) {
                language
            } else {
                "$language • $transliteration"
            }
        )
    }

    private suspend fun fetchDoc(wiki: WikiSite, pageTitlePrefix: String, age: Int): Document? {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { add(Calendar.DATE, -age) }
        val monthDayFormat = SimpleDateFormat("MMMM_d", Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val pageTitle = "$pageTitlePrefix/${calendar[Calendar.YEAR]}/${monthDayFormat.format(calendar.time)}"

        val html = ServiceFactory.get(wiki).parsePage(pageTitle).text
        if (html.isEmpty()) {
            return null
        }
        return Jsoup.parse(html)
    }
}
