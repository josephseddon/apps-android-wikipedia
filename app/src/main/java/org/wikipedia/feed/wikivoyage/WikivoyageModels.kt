package org.wikipedia.feed.wikivoyage

enum class WikivoyageSpotlightType {
    DESTINATION_OF_THE_MONTH,
    OFF_THE_BEATEN_PATH,
    FEATURED_TRAVEL_TOPIC
}

data class WikivoyageSpotlight(
    val type: WikivoyageSpotlightType,
    val articleTitle: String,
    val blurb: String,
    val imageUrl: String?
)

data class WikivoyageDiscover(
    val facts: List<String>,
    val imageUrl: String?
)

data class WikivoyageMainPageData(
    val spotlights: Map<WikivoyageSpotlightType, WikivoyageSpotlight>,
    val discover: WikivoyageDiscover?
)

data class WikivoyageArchiveEntry(
    val year: Int,
    val month: String,
    val articleTitle: String,
    val caption: String,
    val imageUrl: String?
)

data class WikivoyageDiscoverArchiveGroup(
    val heading: String,
    val facts: List<String>,
    val imageUrl: String?
)
