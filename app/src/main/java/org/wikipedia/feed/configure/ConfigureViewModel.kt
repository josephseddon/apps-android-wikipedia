package org.wikipedia.feed.configure

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.wikipedia.util.Resource

class ConfigureViewModel() : ViewModel() {

    private val _uiState = MutableStateFlow<Resource<Boolean>>(Resource.Success(true))
    val uiState = _uiState.asStateFlow()
}
