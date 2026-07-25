package org.wikipedia.feed.wotd

import org.wikipedia.R
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.feed.featured.FeaturedArticleCard
import org.wikipedia.feed.model.CardType
import org.wikipedia.history.HistoryEntry
import org.wikipedia.util.L10nUtil

class ForeignWordOfTheDayCard(
    page: PageSummary,
    age: Int,
    wiki: WikiSite
) : FeaturedArticleCard(page, age, wiki) {

    override fun title(): String {
        return L10nUtil.getString(wikiSite().languageCode, R.string.view_foreign_word_of_the_day_card_title)
    }

    override fun type(): CardType {
        return CardType.FOREIGN_WORD_OF_THE_DAY
    }

    override fun historyEntrySource(): Int {
        return HistoryEntry.SOURCE_FEED_FOREIGN_WORD_OF_THE_DAY
    }

    override fun footerActionText(): String {
        return L10nUtil.getString(wikiSite().languageCode, R.string.view_word_of_the_day_card_footer)
    }
}
