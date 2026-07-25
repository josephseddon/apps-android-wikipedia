package org.wikipedia.feed.model

import android.content.Context
import org.wikipedia.extensions.getByCode
import org.wikipedia.feed.FeedContentType
import org.wikipedia.feed.accessibility.AccessibilityCardView
import org.wikipedia.feed.announcement.AnnouncementCardView
import org.wikipedia.feed.becauseyouread.BecauseYouReadCardView
import org.wikipedia.feed.dayheader.DayHeaderCardView
import org.wikipedia.feed.offline.OfflineCardView
import org.wikipedia.feed.progress.ProgressCardView
import org.wikipedia.feed.random.RandomCardView
import org.wikipedia.feed.searchbar.SearchCardView
import org.wikipedia.feed.topread.TopReadCardView
import org.wikipedia.feed.view.FeedCardView
import org.wikipedia.feed.wotd.ForeignWordOfTheDayCardView
import org.wikipedia.feed.wotd.WordOfTheDayCardView
import org.wikipedia.model.EnumCode

enum class CardType(
    private val code: Int,
    private val contentType: FeedContentType? = null
) : EnumCode {
    SEARCH_BAR(0) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return SearchCardView(ctx)
        }
    },
    BECAUSE_YOU_READ_LIST(2, FeedContentType.BECAUSE_YOU_READ) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return BecauseYouReadCardView(ctx)
        }
    },
    TOP_READ_LIST(3, FeedContentType.TOP_READ_ARTICLES) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return TopReadCardView(ctx)
        }
    },
    RANDOM(5, FeedContentType.RANDOM) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return RandomCardView(ctx)
        }
    },
    BECAUSE_YOU_READ_ITEM(9), MOST_READ_ITEM(10), ANNOUNCEMENT(13) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AnnouncementCardView(ctx)
        }
    },
    SURVEY(14) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AnnouncementCardView(ctx)
        }
    },
    FUNDRAISING(15) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AnnouncementCardView(ctx)
        }
    },
    ONBOARDING_OFFLINE(17) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AnnouncementCardView(ctx)
        }
    },
    ONBOARDING_CUSTOMIZE_FEED(19) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AnnouncementCardView(ctx)
        }
    },
    ACCESSIBILITY(22, FeedContentType.ACCESSIBILITY) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return AccessibilityCardView(ctx)
        }
    },
    WORD_OF_THE_DAY(25, FeedContentType.WORD_OF_THE_DAY) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return WordOfTheDayCardView(ctx)
        }
    },
    FOREIGN_WORD_OF_THE_DAY(26, FeedContentType.FOREIGN_WORD_OF_THE_DAY) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return ForeignWordOfTheDayCardView(ctx)
        }
    },
    DAY_HEADER(97) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return DayHeaderCardView(ctx)
        }
    },
    OFFLINE(98) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return OfflineCardView(ctx)
        }
    },
    PROGRESS(99) {
        override fun newView(ctx: Context): FeedCardView<*> {
            return ProgressCardView(ctx)
        }
    };

    override fun code(): Int {
        return code
    }

    open fun newView(ctx: Context): FeedCardView<*> {
        throw UnsupportedOperationException()
    }

    fun contentType(): FeedContentType? {
        return contentType
    }

    companion object {
        fun of(code: Int): CardType {
            return entries.getByCode(code)
        }
    }
}
