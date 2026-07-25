package org.wikipedia.feed.wikivoyage

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.wikipedia.util.UriUtil

object WikivoyageMainPageParser {

    fun parse(html: String): WikivoyageMainPageData {
        val doc = Jsoup.parse(html)
        val spotlights = doc.select(".jcarousel-item").mapNotNull { parseSpotlight(it) }
            .associateBy { it.type }
        return WikivoyageMainPageData(spotlights, parseDiscover(doc))
    }

    private fun parseSpotlight(item: Element): WikivoyageSpotlight? {
        val shadowBox = item.selectFirst(".mainpage-shadowbox") ?: return null
        val type = shadowBox.selectFirst("h2 a")?.attr("href")?.let { spotlightTypeFromHref(it) } ?: return null
        val titleLink = shadowBox.selectFirst("h3 a") ?: return null
        val articleTitle = UriUtil.getTitleFromUrl(titleLink.attr("href")).ifEmpty { titleLink.text() }
        val quoteEl = shadowBox.selectFirst(".quote a") ?: shadowBox.selectFirst(".quote")
        val blurb = quoteEl?.text().orEmpty()
        val imageUrl = item.selectFirst(".banner-image")?.select("img")
            ?.maxByOrNull { it.attr("width").toIntOrNull() ?: 0 }
            ?.attr("src")?.takeIf { it.isNotEmpty() }
            ?.let { UriUtil.resolveProtocolRelativeUrl(it) }
        return WikivoyageSpotlight(type, articleTitle, blurb, imageUrl)
    }

    private fun spotlightTypeFromHref(href: String): WikivoyageSpotlightType? {
        val lower = href.lowercase()
        return when {
            lower.contains("destinations_of_the_month") -> WikivoyageSpotlightType.DESTINATION_OF_THE_MONTH
            lower.contains("beaten_path") -> WikivoyageSpotlightType.OFF_THE_BEATEN_PATH
            lower.contains("travel_topics") -> WikivoyageSpotlightType.FEATURED_TRAVEL_TOPIC
            else -> null
        }
    }

    private fun parseDiscover(doc: org.jsoup.nodes.Document): WikivoyageDiscover? {
        val container = doc.selectFirst("#mainpage-welcome") ?: return null
        val imageUrl = container.selectFirst("figure img")?.attr("src")?.takeIf { it.isNotEmpty() }
            ?.let { UriUtil.resolveProtocolRelativeUrl(it) }
        val facts = container.select("ul li").map { it.html() }
        return facts.takeIf { it.isNotEmpty() }?.let { WikivoyageDiscover(it, imageUrl) }
    }
}
