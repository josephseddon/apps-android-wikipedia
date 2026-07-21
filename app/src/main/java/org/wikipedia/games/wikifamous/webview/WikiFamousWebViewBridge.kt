package org.wikipedia.games.wikifamous.webview

import android.webkit.JavascriptInterface
import org.wikipedia.games.wikifamous.WikiFamousGameViewModel
import org.wikipedia.json.JsonUtil
import org.wikipedia.util.UiState

/**
 * JS bridge exposed to the bundled WikiFamous WebView game screens. All game logic (round
 * generation, scoring, persistence) stays in the shared [WikiFamousGameViewModel] so the
 * WebView and native implementations cannot drift from each other; this bridge only relays
 * state in and out of it. Methods are called from a WebView background thread.
 */
class WikiFamousWebViewBridge(
    private val viewModel: WikiFamousGameViewModel,
    private val themeJson: String
) {
    @JavascriptInterface
    @Synchronized
    fun getGameStateJson(): String {
        val state = (viewModel.uiState.value as? UiState.Success)?.data ?: return "null"
        return JsonUtil.encodeToString(state).orEmpty()
    }

    @JavascriptInterface
    @Synchronized
    fun submitAnswer(selectedTitle: String): String {
        viewModel.submitAnswer(selectedTitle)
        return getGameStateJson()
    }

    @JavascriptInterface
    @Synchronized
    fun goToNextRound(): String {
        viewModel.goToNextRound()
        return getGameStateJson()
    }

    @JavascriptInterface
    @Synchronized
    fun getThemeJson(): String = themeJson

    @JavascriptInterface
    @Synchronized
    fun getSpeedBonusWindowMs(): Long = WikiFamousGameViewModel.SPEED_BONUS_WINDOW_MS
}
