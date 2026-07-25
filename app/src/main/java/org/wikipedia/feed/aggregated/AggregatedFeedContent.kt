package org.wikipedia.feed.aggregated

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.wikipedia.feed.didyouknow.DidYouKnowItem
import org.wikipedia.feed.topread.TopRead

@Serializable
class AggregatedFeedContent(
    @SerialName("mostread") val topRead: TopRead? = null,
    val dyk: List<DidYouKnowItem>? = null
)
