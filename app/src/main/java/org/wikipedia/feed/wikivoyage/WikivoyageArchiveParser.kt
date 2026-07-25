package org.wikipedia.feed.wikivoyage

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.wikipedia.util.UriUtil

object WikivoyageArchiveParser {

    fun parseSpotlightArchive(html: String): List<WikivoyageArchiveEntry> {
        val content = Jsoup.parse(html).let { it.selectFirst(".mw-parser-output") ?: it.body() } ?: return emptyList()
        val entries = mutableListOf<WikivoyageArchiveEntry>()
        var currentYear: Int? = null
        content.children().forEach { el ->
            if (el.tagName() == "div" && el.hasClass("mw-heading3")) {
                currentYear = el.selectFirst("h3")?.let { it.attr("id").toIntOrNull() ?: it.text().trim().toIntOrNull() }
            } else if (el.tagName() == "table") {
                currentYear?.let { year -> entries += parseSpotlightTable(el, year) }
            }
        }
        return entries
    }

    private fun parseSpotlightTable(table: Element, year: Int): List<WikivoyageArchiveEntry> {
        return table.select("td").mapNotNull { td ->
            val month = td.selectFirst("div b")?.text()?.trim().takeUnless { it.isNullOrEmpty() } ?: return@mapNotNull null
            val figure = td.selectFirst("figure") ?: return@mapNotNull null
            val link = figure.selectFirst("a[href^=/wiki/]") ?: return@mapNotNull null
            val articleTitle = UriUtil.getTitleFromUrl(link.attr("href"))
            val imageUrl = figure.selectFirst("img")?.attr("src")?.takeIf { it.isNotEmpty() }
                ?.let { UriUtil.resolveProtocolRelativeUrl(it) }
            val figcaption = figure.selectFirst("figcaption")
            figcaption?.select(".listing-coordinates, .mw-kartographer-maplink, style")?.remove()
            val caption = figcaption?.text().orEmpty().replace(Regex("\\s+,"), ",").trim()
            WikivoyageArchiveEntry(year, month, articleTitle, caption, imageUrl)
        }
    }

    fun parseDiscoverArchive(html: String): List<WikivoyageDiscoverArchiveGroup> {
        val content = Jsoup.parse(html).let { it.selectFirst(".mw-parser-output") ?: it.body() } ?: return emptyList()
        val groups = mutableListOf<WikivoyageDiscoverArchiveGroup>()
        var currentHeading: String? = null
        var pendingImage: String? = null
        content.children().forEach { el ->
            when {
                el.tagName() == "div" && el.hasClass("mw-heading2") -> {
                    currentHeading = el.selectFirst("h2")?.text()?.trim()
                    pendingImage = null
                }
                el.tagName() == "figure" -> {
                    pendingImage = el.selectFirst("img")?.attr("src")?.takeIf { it.isNotEmpty() }
                        ?.let { UriUtil.resolveProtocolRelativeUrl(it) }
                }
                el.tagName() == "ul" -> {
                    currentHeading?.let { heading ->
                        val facts = el.select("li").map { it.html() }
                        if (facts.isNotEmpty()) {
                            groups += WikivoyageDiscoverArchiveGroup(heading, facts, pendingImage)
                        }
                    }
                    pendingImage = null
                }
            }
        }
        return groups
    }
}
