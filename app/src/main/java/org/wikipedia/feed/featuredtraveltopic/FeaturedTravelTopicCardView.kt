package org.wikipedia.feed.featuredtraveltopic

import android.content.Context
import org.wikipedia.R
import org.wikipedia.feed.featured.FeaturedArticleCardView
import org.wikipedia.feed.view.CardFooterView
import org.wikipedia.wikivoyage.archive.WikivoyageSpotlightArchiveActivity

class FeaturedTravelTopicCardView(context: Context) : FeaturedArticleCardView(context) {

    override val footerCallback: CardFooterView.Callback
        get() = CardFooterView.Callback {
            card?.let {
                context.startActivity(
                    WikivoyageSpotlightArchiveActivity.newIntent(
                        context, it.wikiSite(), ARCHIVE_PAGE_TITLE,
                        context.getString(R.string.view_featured_travel_topic_card_title)
                    )
                )
            }
        }

    companion object {
        const val ARCHIVE_PAGE_TITLE = "Previous_Featured_travel_topics"
    }
}
