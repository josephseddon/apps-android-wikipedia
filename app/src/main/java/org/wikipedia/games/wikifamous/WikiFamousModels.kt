package org.wikipedia.games.wikifamous

import kotlinx.serialization.Serializable

@Serializable
data class WikiFamousArticle(
    val title: String = "",
    val extract: String = "",
    val thumbnailUrl: String? = null,
    val views: Long = 0
)

@Serializable
data class WikiFamousRound(
    val article1: WikiFamousArticle,
    val article2: WikiFamousArticle,
    val selectedTitle: String? = null,
    val answeredCorrectly: Boolean? = null,
    val respondedWithinBonusWindow: Boolean = false
) {
    val answered get() = selectedTitle != null
    val winningArticle get() = if (article1.views >= article2.views) article1 else article2
}

@Serializable
data class WikiFamousGameStatistics(
    val totalGamesPlayed: Int = 0,
    val averageScore: Double? = null,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0
)

sealed class WikiFamousCardGameState {
    data object NotPlayed : WikiFamousCardGameState()
    data class InProgress(val currentQuestion: Int) : WikiFamousCardGameState()
    data class Completed(val score: Int, val totalQuestions: Int) : WikiFamousCardGameState()
}
