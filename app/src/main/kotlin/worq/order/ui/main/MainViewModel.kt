package worq.order.ui.main

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainViewModel : ViewModel() {
    private val mutableUiState = MutableStateFlow(MainUiState())

    val uiState: StateFlow<MainUiState> = mutableUiState.asStateFlow()
}
