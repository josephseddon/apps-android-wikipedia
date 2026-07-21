package org.wikipedia.games.wikifamous.webview

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import org.wikipedia.R
import org.wikipedia.compose.components.WikiTopAppBar
import org.wikipedia.compose.components.error.WikiErrorClickEvents
import org.wikipedia.compose.components.error.WikiErrorView
import org.wikipedia.compose.theme.WikipediaColor
import org.wikipedia.compose.theme.WikipediaTheme
import org.wikipedia.games.db.DailyGameHistory
import org.wikipedia.games.wikifamous.WikiFamousGameViewModel
import org.wikipedia.games.wikifamous.WikiFamousResultsScreen
import org.wikipedia.util.UiState

@Composable
fun WikiFamousWebViewGameScreen(
    viewModel: WikiFamousGameViewModel,
    onBackClick: () -> Unit,
    onReadArticle: (String) -> Unit
) {
    val uiState = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            WikiTopAppBar(
                title = stringResource(R.string.wiki_famous_webview_game_title),
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
                            game = viewModel.game,
                            onReadArticle = onReadArticle,
                            onDone = onBackClick
                        )
                    } else {
                        WikiFamousWebView(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
@Composable
private fun WikiFamousWebView(viewModel: WikiFamousGameViewModel) {
    val colors = WikipediaTheme.colors
    val themeJson = remember(colors) { colors.toThemeJson() }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // The game bundle never navigates away from its own assets.
                        return !request.url.toString().startsWith(WIKIFAMOUS_ASSET_BASE_URL)
                    }
                }
                addJavascriptInterface(WikiFamousWebViewBridge(viewModel, themeJson), "WikiFamousBridge")
                loadUrl("${WIKIFAMOUS_ASSET_BASE_URL}index.html")
            }
        }
    )
}

private fun WikipediaColor.toThemeJson(): String {
    return "{" +
        "\"primary\":\"${primaryColor.toHex()}\"," +
        "\"secondary\":\"${secondaryColor.toHex()}\"," +
        "\"paper\":\"${paperColor.toHex()}\"," +
        "\"background\":\"${backgroundColor.toHex()}\"," +
        "\"border\":\"${borderColor.toHex()}\"," +
        "\"success\":\"${successColor.toHex()}\"," +
        "\"destructive\":\"${destructiveColor.toHex()}\"," +
        "\"warning\":\"${warningColor.toHex()}\"," +
        "\"progressive\":\"${progressiveColor.toHex()}\"" +
        "}"
}

private fun Color.toHex(): String {
    return String.format("#%06X", 0xFFFFFF and toArgb())
}

private const val WIKIFAMOUS_ASSET_BASE_URL = "file:///android_asset/wikifamous/"
