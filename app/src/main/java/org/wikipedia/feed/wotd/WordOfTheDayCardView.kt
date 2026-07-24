package org.wikipedia.feed.wotd

import android.content.Context
import org.wikipedia.feed.featured.FeaturedArticleCardView
import org.wikipedia.feed.view.CardFooterView

class WordOfTheDayCardView(context: Context) : FeaturedArticleCardView(context) {

    override val footerCallback: CardFooterView.Callback?
        get() = CardFooterView.Callback {
            card?.let {
                callback?.onSelectPage(it, it.historyEntry(), false)
            }
        }
}
