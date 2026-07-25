package org.wikipedia.feed.configure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.wikipedia.WikipediaApp
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.feed.FeedContentType
import org.wikipedia.util.Resource

class ConfigureViewModel() : ViewModel() {

    private val handler = CoroutineExceptionHandler { _, throwable ->
        _uiState.value = Resource.Error(throwable)
    }

    private val _uiState = MutableStateFlow(Resource<Boolean>())
    val uiState = _uiState.asStateFlow()

    init {
        loadFeedAvailability()
    }

    private fun loadFeedAvailability() {
        _uiState.value = Resource.Loading()
        viewModelScope.launch(handler) {
            val result = ServiceFactory.getRest(WikipediaApp.instance.wikiSite).feedAvailability()
            // apply the new availability rules to our content types
            FeedContentType.TOP_READ_ARTICLES.langCodesSupported.clear()
            if (isLimitedToDomains(result.mostRead)) {
                addDomainNamesAsLangCodes(FeedContentType.TOP_READ_ARTICLES.langCodesSupported, result.mostRead)
            }
            FeedContentType.saveState()
            _uiState.value = Resource.Success(true)
        }
    }

    private fun isLimitedToDomains(domainNames: List<String>): Boolean {
        return domainNames.isNotEmpty() && !domainNames[0].contains("*")
    }

    private fun addDomainNamesAsLangCodes(outList: MutableList<String>, domainNames: List<String>) {
        outList.addAll(domainNames.map { WikiSite(it).languageCode })
    }
}
