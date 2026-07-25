package org.wikipedia.feed.discover

import android.net.Uri
import androidx.core.net.toUri
import org.wikipedia.R
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.model.CardType
import org.wikipedia.feed.model.WikiSiteCard
import org.wikipedia.feed.wikivoyage.WikivoyageDiscover
import org.wikipedia.util.DateUtil
import org.wikipedia.util.L10nUtil

class DiscoverCard(
    private val discover: WikivoyageDiscover,
    private val age: Int,
    wiki: WikiSite
) : WikiSiteCard(wiki) {

    override fun title(): String {
        return L10nUtil.getString(wikiSite().languageCode, R.string.view_discover_card_title)
    }

    override fun subtitle(): String {
        return DateUtil.getFeedCardDateString(age)
    }

    override fun image(): Uri? {
        return discover.imageUrl?.toUri()
    }

    fun facts(): List<String> {
        return discover.facts
    }

    override fun type(): CardType {
        return CardType.DISCOVER
    }

    override fun dismissHashCode(): Int {
        return discover.facts.hashCode()
    }
}
