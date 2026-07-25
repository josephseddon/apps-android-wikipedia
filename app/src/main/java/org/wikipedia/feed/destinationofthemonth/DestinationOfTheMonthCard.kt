package org.wikipedia.feed.destinationofthemonth

import org.wikipedia.R
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.feed.featured.FeaturedArticleCard
import org.wikipedia.feed.model.CardType
import org.wikipedia.feed.wikivoyage.WikivoyageSpotlight
import org.wikipedia.history.HistoryEntry
import org.wikipedia.util.L10nUtil

class DestinationOfTheMonthCard(
    spotlight: WikivoyageSpotlight,
    age: Int,
    wiki: WikiSite
) : FeaturedArticleCard(
    PageSummary(spotlight.articleTitle, spotlight.articleTitle, null, spotlight.blurb, spotlight.imageUrl, wiki.languageCode),
    age,
    wiki
) {
    override fun title(): String {
        return L10nUtil.getString(wikiSite().languageCode, R.string.view_destination_of_the_month_card_title)
    }

    override fun footerActionText(): String {
        return L10nUtil.getString(wikiSite().languageCode, R.string.view_wikivoyage_archive_action)
    }

    override fun type(): CardType {
        return CardType.DESTINATION_OF_THE_MONTH
    }

    override fun historyEntrySource(): Int {
        return HistoryEntry.SOURCE_FEED_DESTINATION_OF_THE_MONTH
    }
}
