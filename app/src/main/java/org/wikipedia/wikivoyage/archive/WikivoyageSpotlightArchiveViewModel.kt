package org.wikipedia.wikivoyage.archive

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.wikivoyage.WikivoyageArchiveEntry
import org.wikipedia.feed.wikivoyage.WikivoyageArchiveParser
import org.wikipedia.util.UiState
import org.wikipedia.util.log.L

class WikivoyageSpotlightArchiveViewModel(
    private val wiki: WikiSite,
    private val archivePageTitle: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<WikivoyageArchiveEntry>>>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val handler = CoroutineExceptionHandler { _, throwable ->
        L.e(throwable)
        _uiState.value = UiState.Error(throwable)
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch(handler) {
            _uiState.value = UiState.Loading
            val html = ServiceFactory.get(wiki).parsePage(archivePageTitle).text
            _uiState.value = UiState.Success(WikivoyageArchiveParser.parseSpotlightArchive(html))
        }
    }

    class Factory(private val wiki: WikiSite, private val archivePageTitle: String) : ViewModelProvider.Factory {
        @Suppress("unchecked_cast")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return WikivoyageSpotlightArchiveViewModel(wiki, archivePageTitle) as T
        }
    }
}
