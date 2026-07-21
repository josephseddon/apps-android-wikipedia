package org.wikipedia.games.wikifamous

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.wikipedia.R
import org.wikipedia.compose.components.AppButton
import org.wikipedia.compose.components.FadeInAsyncImage
import org.wikipedia.compose.components.WikiCard
import org.wikipedia.compose.components.WikiTopAppBar
import org.wikipedia.compose.components.error.WikiErrorClickEvents
import org.wikipedia.compose.components.error.WikiErrorView
import org.wikipedia.compose.theme.BaseTheme
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.games.db.DailyGameHistory
import org.wikipedia.theme.Theme
import org.wikipedia.util.UiState
import org.wikipedia.views.imageservice.ImageService
import java.util.Locale

@Composable
fun WikiFamousGameScreen(
    viewModel: WikiFamousGameViewModel,
    onBackClick: () -> Unit,
    onReadArticle: (String) -> Unit
) {
    val uiState = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.wiki_famous_game_title),
                onNavigationClick = onBackClick
            )
        },
        containerColor = WikipediaTheme.colors.paperColor
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is UiState.Loading -> CircularProgressIndicator(color = WikipediaTheme.colors.progressiveColor)
                is UiState.Error -> WikiErrorView(
                    modifier = Modifier.fillMaxWidth(),
                    caught = uiState.error,
                    errorClickEvents = WikiErrorClickEvents { viewModel.loadGameState() },
                    retryForGenericError = true
                )
                is UiState.Success -> {
                    val state = uiState.data
                    if (state.status == DailyGameHistory.GAME_COMPLETED) {
                        WikiFamousResultsScreen(
                            state = state,
                            wikiSite = viewModel.wikiSite,
                            onReadArticle = onReadArticle,
                            onDone = onBackClick
                        )
                    } else {
                        WikiFamousPlayScreen(
                            state = state,
                            onSelectArticle = viewModel::submitAnswer,
                            onNext = viewModel::goToNextRound
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WikiFamousPlayScreen(
    state: WikiFamousGameViewModel.GameState,
    onSelectArticle: (String) -> Unit,
    onNext: () -> Unit
) {
    val round = state.rounds.getOrNull(state.currentRoundIndex) ?: return
    var elapsedMs by remember(state.currentRoundIndex) { mutableLongStateOf(0L) }

    LaunchedEffect(state.currentRoundIndex, round.answered) {
        if (!round.answered) {
            val start = System.currentTimeMillis()
            while (true) {
                elapsedMs = System.currentTimeMillis() - start
                delay(100)
            }
        }
    }

    val withinBonusWindow = elapsedMs <= WikiFamousGameViewModel.SPEED_BONUS_WINDOW_MS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.wiki_famous_game_round_label, state.currentRoundIndex + 1, state.rounds.size),
                style = MaterialTheme.typography.labelLarge,
                color = WikipediaTheme.colors.secondaryColor
            )
            Text(
                text = stringResource(R.string.wiki_famous_game_score_value, state.score),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = WikipediaTheme.colors.primaryColor
            )
        }

        if (!round.answered) {
            Text(
                text = String.format(Locale.getDefault(), "%.1fs", elapsedMs / 1000f),
                style = MaterialTheme.typography.labelMedium,
                color = if (withinBonusWindow) WikipediaTheme.colors.successColor else WikipediaTheme.colors.placeholderColor,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.wiki_famous_game_question_prompt),
            style = MaterialTheme.typography.titleMedium,
            color = WikipediaTheme.colors.primaryColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        WikiFamousArticleCard(
            article = round.article1,
            isAnswered = round.answered,
            isSelected = round.selectedTitle == round.article1.title,
            isWinner = round.winningArticle.title == round.article1.title,
            onClick = { onSelectArticle(round.article1.title) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        WikiFamousArticleCard(
            article = round.article2,
            isAnswered = round.answered,
            isSelected = round.selectedTitle == round.article2.title,
            isWinner = round.winningArticle.title == round.article2.title,
            onClick = { onSelectArticle(round.article2.title) }
        )

        if (round.answered) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (round.answeredCorrectly == true) stringResource(R.string.wiki_famous_game_correct) else stringResource(R.string.wiki_famous_game_incorrect),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (round.answeredCorrectly == true) WikipediaTheme.colors.successColor else WikipediaTheme.colors.destructiveColor
            )
            if (round.respondedWithinBonusWindow) {
                Text(
                    text = stringResource(R.string.wiki_famous_game_speed_bonus, WikiFamousGameViewModel.SPEED_BONUS_POINTS),
                    style = MaterialTheme.typography.bodyMedium,
                    color = WikipediaTheme.colors.warningColor
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            AppButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.wiki_famous_game_next))
            }
        }
    }
}

@Composable
private fun WikiFamousArticleCard(
    article: WikiFamousArticle,
    isAnswered: Boolean,
    isSelected: Boolean,
    isWinner: Boolean,
    onClick: () -> Unit
) {
    val borderColor: Color? = when {
        !isAnswered -> null
        isWinner -> WikipediaTheme.colors.successColor
        isSelected -> WikipediaTheme.colors.destructiveColor
        else -> null
    }

    WikiCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (isAnswered) null else onClick,
        border = borderColor?.let { BorderStroke(2.dp, it) }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            article.thumbnailUrl?.let { url ->
                FadeInAsyncImage(
                    model = ImageService.getRequest(LocalContext.current, url),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = WikipediaTheme.colors.primaryColor
                )
                Text(
                    text = article.extract,
                    style = MaterialTheme.typography.bodySmall,
                    color = WikipediaTheme.colors.secondaryColor,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (isAnswered) {
                    Text(
                        text = stringResource(R.string.wiki_famous_game_views_count, String.format(Locale.getDefault(), "%,d", article.views)),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isWinner) WikipediaTheme.colors.successColor else WikipediaTheme.colors.secondaryColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun WikiFamousResultsScreen(
    state: WikiFamousGameViewModel.GameState,
    wikiSite: WikiSite,
    onReadArticle: (String) -> Unit,
    onDone: () -> Unit
) {
    var stats by remember { mutableStateOf<WikiFamousGameStatistics?>(null) }
    LaunchedEffect(Unit) {
        stats = WikiFamousGameViewModel.getGameStatistics(wikiSite)
    }

    val correctCount = state.rounds.count { it.answeredCorrectly == true }
    val wrongCount = state.rounds.size - correctCount
    val bonusCount = state.rounds.count { it.respondedWithinBonusWindow }
    val basePoints = correctCount * WikiFamousGameViewModel.CORRECT_POINTS + wrongCount * WikiFamousGameViewModel.WRONG_POINTS
    val bonusPoints = bonusCount * WikiFamousGameViewModel.SPEED_BONUS_POINTS
    val accuracy = if (state.rounds.isNotEmpty()) correctCount * 100 / state.rounds.size else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.wiki_famous_game_final_results),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            color = WikipediaTheme.colors.primaryColor
        )
        Spacer(modifier = Modifier.height(16.dp))

        WikiCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                ResultRow(stringResource(R.string.wiki_famous_game_base_points), basePoints.toString())
                ResultRow(stringResource(R.string.wiki_famous_game_bonus_points), "+$bonusPoints")
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = WikipediaTheme.colors.borderColor)
                ResultRow(stringResource(R.string.wiki_famous_game_final_score), state.score.toString(), emphasize = true)
                ResultRow(stringResource(R.string.wiki_famous_game_accuracy), "$accuracy%")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        stats?.let { gameStats ->
            Text(
                text = stringResource(R.string.wiki_famous_game_your_stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = WikipediaTheme.colors.primaryColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            WikiCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ResultRow(stringResource(R.string.wiki_famous_game_stats_streak), gameStats.currentStreak.toString())
                    ResultRow(stringResource(R.string.wiki_famous_game_stats_best_streak), gameStats.bestStreak.toString())
                    ResultRow(
                        stringResource(R.string.wiki_famous_game_stats_average_score),
                        gameStats.averageScore?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "--"
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = stringResource(R.string.wiki_famous_game_result_subtitle),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = WikipediaTheme.colors.primaryColor
        )
        Spacer(modifier = Modifier.height(8.dp))

        state.rounds.forEach { round ->
            val article = round.winningArticle
            WikiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                onClick = { onReadArticle(article.title) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(
                            if (round.answeredCorrectly == true) R.drawable.ic_check_circle_black_24dp else R.drawable.ic_cancel_24px
                        ),
                        contentDescription = null,
                        tint = if (round.answeredCorrectly == true) WikipediaTheme.colors.successColor else WikipediaTheme.colors.destructiveColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = WikipediaTheme.colors.primaryColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.wiki_famous_game_read_article),
                        style = MaterialTheme.typography.labelLarge,
                        color = WikipediaTheme.colors.progressiveColor
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        AppButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.wiki_famous_game_done_button))
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = WikipediaTheme.colors.secondaryColor
        )
        Text(
            text = value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
            color = WikipediaTheme.colors.primaryColor
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WikiFamousPlayScreenPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        WikiFamousPlayScreen(
            state = WikiFamousGameViewModel.GameState(
                rounds = listOf(
                    WikiFamousRound(
                        article1 = WikiFamousArticle(
                            title = "Albert Einstein",
                            extract = "German-born theoretical physicist who developed the theory of relativity.",
                            views = 1_200_000
                        ),
                        article2 = WikiFamousArticle(
                            title = "Marie Curie",
                            extract = "Polish and naturalized-French physicist and chemist who conducted pioneering research on radioactivity.",
                            views = 800_000
                        )
                    )
                ),
                currentRoundIndex = 0,
                score = 10
            ),
            onSelectArticle = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WikiFamousResultsScreenPreview() {
    BaseTheme(currentTheme = Theme.LIGHT) {
        WikiFamousResultsScreen(
            state = WikiFamousGameViewModel.GameState(
                rounds = listOf(
                    WikiFamousRound(
                        article1 = WikiFamousArticle(title = "Albert Einstein", extract = "Theoretical physicist.", views = 1_200_000),
                        article2 = WikiFamousArticle(title = "Marie Curie", extract = "Physicist and chemist.", views = 800_000),
                        selectedTitle = "Albert Einstein",
                        answeredCorrectly = true,
                        respondedWithinBonusWindow = true
                    )
                ),
                currentRoundIndex = 1,
                score = 15,
                status = DailyGameHistory.GAME_COMPLETED
            ),
            wikiSite = WikiSite.forLanguageCode("en"),
            onReadArticle = {},
            onDone = {}
        )
    }
}
