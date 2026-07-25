package org.wikipedia.feed

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import org.wikipedia.R
import org.wikipedia.feed.accessibility.AccessibilityCardClient
import org.wikipedia.feed.becauseyouread.BecauseYouReadClient
import org.wikipedia.feed.dataclient.FeedClient
import org.wikipedia.feed.destinationofthemonth.DestinationOfTheMonthClient
import org.wikipedia.feed.discover.DiscoverClient
import org.wikipedia.feed.featuredtraveltopic.FeaturedTravelTopicClient
import org.wikipedia.feed.offthebeatenpath.OffTheBeatenPathClient
import org.wikipedia.feed.places.PlacesFeedClient
import org.wikipedia.feed.random.RandomClient
import org.wikipedia.model.EnumCode
import org.wikipedia.settings.Prefs
import org.wikipedia.util.DeviceUtil

enum class FeedContentType(private val code: Int,
                           @StringRes val titleId: Int,
                           @StringRes val subtitleId: Int,
                           val isPerLanguage: Boolean,
                           var showInConfig: Boolean = true) : EnumCode {
    BECAUSE_YOU_READ(8, R.string.view_because_you_read_card_title, R.string.feed_item_type_because_you_read, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) BecauseYouReadClient(coroutineScope) else null
        }
    },
    RANDOM(5, R.string.view_random_card_title, R.string.feed_item_type_randomizer, true) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) RandomClient(coroutineScope) else null
        }
    },
    PLACES(11, R.string.places_title, R.string.feed_item_type_places, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) PlacesFeedClient(coroutineScope) else null
        }
    },
    DESTINATION_OF_THE_MONTH(12, R.string.view_destination_of_the_month_card_title, R.string.feed_item_type_destination_of_the_month, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) DestinationOfTheMonthClient(coroutineScope) else null
        }
    },
    OFF_THE_BEATEN_PATH(13, R.string.view_off_the_beaten_path_card_title, R.string.feed_item_type_off_the_beaten_path, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) OffTheBeatenPathClient(coroutineScope) else null
        }
    },
    FEATURED_TRAVEL_TOPIC(14, R.string.view_featured_travel_topic_card_title, R.string.feed_item_type_featured_travel_topic, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) FeaturedTravelTopicClient(coroutineScope) else null
        }
    },
    DISCOVER(15, R.string.view_discover_card_title, R.string.feed_item_type_discover, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (isEnabled) DiscoverClient(coroutineScope) else null
        }
    },
    ACCESSIBILITY(10, 0, 0, false, false) {
        override fun newClient(coroutineScope: CoroutineScope): FeedClient? {
            return if (DeviceUtil.isAccessibilityEnabled) AccessibilityCardClient() else null
        }
    };

    var order = code
    var isEnabled = true
    val langCodesSupported = mutableListOf<String>()
    val langCodesDisabled = mutableListOf<String>()

    abstract fun newClient(coroutineScope: CoroutineScope): FeedClient?

    override fun code(): Int {
        return code
    }

    companion object {

        fun saveState() {
            val enabledList = mutableListOf<Boolean>()
            val orderList = mutableListOf<Int>()
            val langSupportedMap = mutableMapOf<Int, List<String>>()
            val langDisabledMap = mutableMapOf<Int, List<String>>()
            entries.forEach {
                enabledList.add(it.isEnabled)
                orderList.add(it.order)
                langSupportedMap[it.code] = it.langCodesSupported
                langDisabledMap[it.code] = it.langCodesDisabled
            }
            Prefs.feedCardsEnabled = enabledList
            Prefs.feedCardsOrder = orderList
            Prefs.feedCardsLangSupported = langSupportedMap
            Prefs.feedCardsLangDisabled = langDisabledMap
        }

        fun restoreState() {
            val enabledList = Prefs.feedCardsEnabled
            val orderList = Prefs.feedCardsOrder
            val langSupportedMap = Prefs.feedCardsLangSupported
            val langDisabledMap = Prefs.feedCardsLangDisabled
            // If the set of content types has changed since these were saved (e.g. cards were
            // added or removed), the saved lists no longer line up positionally with `entries` --
            // reusing them by index would silently reassign one card's saved order/enabled state
            // to a completely different card. Fall back to defaults instead of doing that.
            val useSavedOrder = orderList.size == entries.size
            val useSavedEnabled = enabledList.size == entries.size
            entries.forEachIndexed { i, type ->
                type.isEnabled = if (useSavedEnabled) enabledList[i] else true
                type.order = if (useSavedOrder) orderList[i] else i
                type.langCodesSupported.clear()
                langSupportedMap[type.code]?.let {
                    type.langCodesSupported.addAll(it)
                }
                type.langCodesDisabled.clear()
                langDisabledMap[type.code]?.let {
                    type.langCodesDisabled.addAll(it)
                }
            }
            if (!useSavedOrder || !useSavedEnabled) {
                saveState()
            }
        }
    }
}
