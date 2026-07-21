package org.wikipedia.games.wikifamous

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.wikipedia.Constants
import org.wikipedia.database.AppDatabase
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.games.PlayTypes
import org.wikipedia.games.WikiGames
import org.wikipedia.games.db.DailyGameHistory
import org.wikipedia.json.JsonUtil
import org.wikipedia.util.UiState
import org.wikipedia.util.log.L
import java.time.LocalDate

class WikiFamousGameViewModel(savedStateHandle: SavedStateHandle) : ViewModel() {

    val invokeSource = savedStateHandle.get<Constants.InvokeSource>(Constants.INTENT_EXTRA_INVOKE_SOURCE)!!
    val wikiSite = savedStateHandle.get<WikiSite>(Constants.ARG_WIKISITE)!!

    private val _uiState = MutableStateFlow<UiState<GameState>>(UiState.Loading)
    val uiState: StateFlow<UiState<GameState>> = _uiState.asStateFlow()

    private var currentGameId: Int? = null
    private var roundStartTimeMs = 0L
    private val currentDate: LocalDate = LocalDate.now()

    init {
        loadGameState()
    }

    fun loadGameState() {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable ->
            L.e(throwable)
            _uiState.value = UiState.Error(throwable)
        }) {
            _uiState.value = UiState.Loading

            val gameHistory = AppDatabase.instance.dailyGameHistoryDao().findGameHistoryByDate(
                gameName = WikiGames.WIKI_FAMOUS.ordinal,
                language = wikiSite.languageCode,
                year = currentDate.year,
                month = currentDate.monthValue,
                day = currentDate.dayOfMonth
            )
            currentGameId = gameHistory?.id

            val rounds = gameHistory?.gameData?.let { JsonUtil.decodeFromString<List<WikiFamousRound>>(it) }
                ?: WikiFamousGameProvider.generateRounds(wikiSite)

            val state = GameState(
                rounds = rounds,
                currentRoundIndex = gameHistory?.currentQuestionIndex ?: 0,
                score = gameHistory?.score ?: 0,
                status = gameHistory?.status ?: DailyGameHistory.GAME_IN_PROGRESS
            )

            if (gameHistory == null) {
                saveGameProgress(state)
            }

            roundStartTimeMs = System.currentTimeMillis()
            _uiState.value = UiState.Success(state)
        }
    }

    fun submitAnswer(selectedTitle: String) {
        val state = (uiState.value as? UiState.Success)?.data ?: return
        if (state.status == DailyGameHistory.GAME_COMPLETED) return
        val round = state.rounds.getOrNull(state.currentRoundIndex) ?: return
        if (round.answered) return

        val elapsedMs = System.currentTimeMillis() - roundStartTimeMs
        val isCorrect = selectedTitle == round.winningArticle.title
        val withinBonusWindow = elapsedMs <= SPEED_BONUS_WINDOW_MS

        val scoreDelta = when {
            isCorrect && withinBonusWindow -> CORRECT_POINTS + SPEED_BONUS_POINTS
            isCorrect -> CORRECT_POINTS
            else -> WRONG_POINTS
        }

        val updatedRounds = state.rounds.toMutableList().apply {
            set(
                state.currentRoundIndex,
                round.copy(
                    selectedTitle = selectedTitle,
                    answeredCorrectly = isCorrect,
                    respondedWithinBonusWindow = isCorrect && withinBonusWindow
                )
            )
        }

        val newState = state.copy(
            rounds = updatedRounds,
            score = (state.score + scoreDelta).coerceAtLeast(0)
        )
        _uiState.value = UiState.Success(newState)
        saveGameProgress(newState)
    }

    fun goToNextRound() {
        val state = (uiState.value as? UiState.Success)?.data ?: return
        val nextIndex = state.currentRoundIndex + 1
        val isGameOver = nextIndex >= state.rounds.size
        val newState = state.copy(
            currentRoundIndex = nextIndex,
            status = if (isGameOver) DailyGameHistory.GAME_COMPLETED else DailyGameHistory.GAME_IN_PROGRESS
        )
        roundStartTimeMs = System.currentTimeMillis()
        _uiState.value = UiState.Success(newState)
        saveGameProgress(newState)
    }

    private fun saveGameProgress(state: GameState) {
        viewModelScope.launch(CoroutineExceptionHandler { _, throwable -> L.e(throwable) }) {
            val dailyGameHistory = DailyGameHistory(
                id = currentGameId ?: 0,
                gameName = WikiGames.WIKI_FAMOUS.ordinal,
                language = wikiSite.languageCode,
                year = currentDate.year,
                month = currentDate.monthValue,
                day = currentDate.dayOfMonth,
                score = state.score,
                playType = PlayTypes.PLAYED_ON_SAME_DAY.ordinal,
                gameData = JsonUtil.encodeToString(state.rounds),
                currentQuestionIndex = state.currentRoundIndex,
                status = state.status
            )
            val resultId = AppDatabase.instance.dailyGameHistoryDao().upsert(dailyGameHistory).toInt()
            currentGameId = if (resultId > 0) resultId else currentGameId
        }
    }

    data class GameState(
        val rounds: List<WikiFamousRound>,
        val currentRoundIndex: Int = 0,
        val score: Int = 0,
        val status: Int = DailyGameHistory.GAME_IN_PROGRESS
    )

    companion object {
        const val CORRECT_POINTS = 10
        const val WRONG_POINTS = -3
        const val SPEED_BONUS_POINTS = 5
        const val SPEED_BONUS_WINDOW_MS = 3000L

        suspend fun getGameStatistics(wikiSite: WikiSite): WikiFamousGameStatistics {
            return withContext(Dispatchers.IO) {
                val totalGamesPlayed = async {
                    AppDatabase.instance.dailyGameHistoryDao().getTotalGamesPlayed(
                        gameName = WikiGames.WIKI_FAMOUS.ordinal,
                        language = wikiSite.languageCode
                    )
                }
                val averageScore = async {
                    AppDatabase.instance.dailyGameHistoryDao().getAverageScore(
                        gameName = WikiGames.WIKI_FAMOUS.ordinal,
                        language = wikiSite.languageCode
                    )
                }
                val currentStreak = async {
                    AppDatabase.instance.dailyGameHistoryDao().getCurrentStreak(
                        gameName = WikiGames.WIKI_FAMOUS.ordinal,
                        language = wikiSite.languageCode
                    )
                }
                val bestStreak = async {
                    AppDatabase.instance.dailyGameHistoryDao().getBestStreak(
                        gameName = WikiGames.WIKI_FAMOUS.ordinal,
                        language = wikiSite.languageCode
                    )
                }
                WikiFamousGameStatistics(
                    totalGamesPlayed = totalGamesPlayed.await(),
                    averageScore = averageScore.await(),
                    currentStreak = currentStreak.await(),
                    bestStreak = bestStreak.await()
                )
            }
        }
    }
}
