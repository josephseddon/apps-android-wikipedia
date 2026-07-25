package org.wikipedia.feed.discover

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import org.wikipedia.R
import org.wikipedia.databinding.ViewCardDiscoverBinding
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.view.CardFooterView
import org.wikipedia.feed.view.DefaultFeedCardView
import org.wikipedia.feed.view.FeedAdapter
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.LinkMovementMethodExt
import org.wikipedia.page.PageTitle
import org.wikipedia.util.StringUtil
import org.wikipedia.util.UriUtil
import org.wikipedia.views.ViewUtil
import org.wikipedia.wikivoyage.archive.WikivoyageDiscoverArchiveActivity

class DiscoverCardView(context: Context) : DefaultFeedCardView<DiscoverCard>(context) {

    private val binding = ViewCardDiscoverBinding.inflate(LayoutInflater.from(context), this, true)

    override var card: DiscoverCard? = null
        set(value) {
            field = value
            value?.let {
                header(it)
                footer(it)
                updateContents(it)
            }
        }

    override var callback: FeedAdapter.Callback? = null
        set(value) {
            field = value
            binding.viewDiscoverCardHeader.setCallback(value)
        }

    private fun updateContents(card: DiscoverCard) {
        card.image()?.let {
            binding.viewDiscoverCardImage.isVisible = true
            ViewUtil.loadImage(binding.viewDiscoverCardImage, it.toString())
        } ?: run {
            binding.viewDiscoverCardImage.isVisible = false
        }
        binding.viewDiscoverCardFacts.movementMethod = movementMethod(card.wikiSite())
        binding.viewDiscoverCardFacts.text = StringUtil.fromHtml(card.facts().firstOrNull().orEmpty())
    }

    private fun movementMethod(wiki: WikiSite): LinkMovementMethodExt {
        return LinkMovementMethodExt { url, _, _ ->
            card?.let { currentCard ->
                val pageTitle = PageTitle(UriUtil.getTitleFromUrl(url), wiki)
                callback?.onSelectPage(currentCard, HistoryEntry(pageTitle, HistoryEntry.SOURCE_FEED_DISCOVER), false)
            }
        }
    }

    private fun header(card: DiscoverCard) {
        binding.viewDiscoverCardHeader.setTitle(card.title())
            .setLangCode(card.wikiSite().languageCode)
            .setCard(card)
            .setCallback(callback)
    }

    private fun footer(card: DiscoverCard) {
        binding.viewDiscoverCardFooter.callback = CardFooterView.Callback {
            context.startActivity(
                WikivoyageDiscoverArchiveActivity.newIntent(
                    context, card.wikiSite(), context.getString(R.string.view_discover_card_title)
                )
            )
        }
        binding.viewDiscoverCardFooter.setFooterActionText(
            context.getString(R.string.view_wikivoyage_archive_action), card.wikiSite().languageCode
        )
    }
}
