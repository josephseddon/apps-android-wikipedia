package org.wikipedia.feed.destinationofthemonth

import android.content.Context
import org.wikipedia.R
import org.wikipedia.feed.featured.FeaturedArticleCardView
import org.wikipedia.feed.view.CardFooterView
import org.wikipedia.wikivoyage.archive.WikivoyageSpotlightArchiveActivity

class DestinationOfTheMonthCardView(context: Context) : FeaturedArticleCardView(context) {

    override val footerCallback: CardFooterView.Callback
        get() = CardFooterView.Callback {
            card?.let {
                context.startActivity(
                    WikivoyageSpotlightArchiveActivity.newIntent(
                        context, it.wikiSite(), ARCHIVE_PAGE_TITLE,
                        context.getString(R.string.view_destination_of_the_month_card_title)
                    )
                )
            }
        }

    companion object {
        const val ARCHIVE_PAGE_TITLE = "Previous_Destinations_of_the_month"
    }
}
