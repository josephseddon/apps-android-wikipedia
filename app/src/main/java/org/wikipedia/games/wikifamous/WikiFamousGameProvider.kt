package org.wikipedia.games.wikifamous

import org.wikipedia.database.AppDatabase
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.dataclient.mwapi.MwQueryPage
import org.wikipedia.games.WikiGames
import org.wikipedia.games.db.DailyGameHistory
import java.time.LocalDate

object WikiFamousGameProvider {
    const val ROUNDS_PER_DAY = 5
    private const val MAX_FETCH_ATTEMPTS = 10

    suspend fun generateRounds(wikiSite: WikiSite): List<WikiFamousRound> {
        val articlesNeeded = ROUNDS_PER_DAY * 2
        val articles = mutableListOf<WikiFamousArticle>()
        val seenTitles = mutableSetOf<String>()
        var attempts = 0
        while (articles.size < articlesNeeded && attempts < MAX_FETCH_ATTEMPTS) {
            attempts++
            val response = ServiceFactory.get(wikiSite).getRandomArticlesWithViews(articlesNeeded)
            response.query?.pages.orEmpty()
                .filter { !it.extract.isNullOrBlank() && seenTitles.add(it.title) }
                .forEach { articles.add(it.toWikiFamousArticle()) }
        }
        return articles.take(articlesNeeded)
            .chunked(2)
            .filter { it.size == 2 }
            .map { (first, second) -> WikiFamousRound(article1 = first, article2 = second) }
    }

    suspend fun getGameState(wikiSite: WikiSite, date: LocalDate, game: WikiGames = WikiGames.WIKI_FAMOUS): WikiFamousCardGameState {
        val gameHistory = AppDatabase.instance.dailyGameHistoryDao().findGameHistoryByDate(
            gameName = game.ordinal,
            language = wikiSite.languageCode,
            year = date.year,
            month = date.monthValue,
            day = date.dayOfMonth
        )
        return when (gameHistory?.status) {
            DailyGameHistory.GAME_COMPLETED -> WikiFamousCardGameState.Completed(
                score = gameHistory.score,
                totalQuestions = ROUNDS_PER_DAY
            )
            DailyGameHistory.GAME_IN_PROGRESS -> WikiFamousCardGameState.InProgress(
                currentQuestion = gameHistory.currentQuestionIndex
            )
            else -> WikiFamousCardGameState.NotPlayed
        }
    }

    private fun MwQueryPage.toWikiFamousArticle(): WikiFamousArticle {
        return WikiFamousArticle(
            title = title,
            extract = extract.orEmpty(),
            thumbnailUrl = thumbUrl(),
            views = pageViewsMap.values.filterNotNull().sum()
        )
    }
}
