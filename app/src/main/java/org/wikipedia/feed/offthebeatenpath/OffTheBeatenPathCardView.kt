package org.wikipedia.feed.offthebeatenpath

import android.content.Context
import org.wikipedia.R
import org.wikipedia.feed.featured.FeaturedArticleCardView
import org.wikipedia.feed.view.CardFooterView
import org.wikipedia.wikivoyage.archive.WikivoyageSpotlightArchiveActivity

class OffTheBeatenPathCardView(context: Context) : FeaturedArticleCardView(context) {

    override val footerCallback: CardFooterView.Callback
        get() = CardFooterView.Callback {
            card?.let {
                context.startActivity(
                    WikivoyageSpotlightArchiveActivity.newIntent(
                        context, it.wikiSite(), ARCHIVE_PAGE_TITLE,
                        context.getString(R.string.view_off_the_beaten_path_card_title)
                    )
                )
            }
        }

    companion object {
        const val ARCHIVE_PAGE_TITLE = "Previously_Off_the_beaten_path"
    }
}
