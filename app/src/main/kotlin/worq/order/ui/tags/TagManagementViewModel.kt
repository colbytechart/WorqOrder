package worq.order.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import worq.order.data.TagRepository
import worq.order.model.TagCategory

class TagManagementViewModel(
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(TagManagementUiState())
    val uiState: StateFlow<TagManagementUiState> = mutableUiState

    private val mutableEffects = MutableSharedFlow<TagManagementEffect>(extraBufferCapacity = 1)
    val effects = mutableEffects.asSharedFlow()
    private var observationJob: Job? = null

    init {
        observeCounts()
    }

    fun onEvent(event: TagManagementEvent) {
        when (event) {
            TagManagementEvent.Retry -> observeCounts()
            TagManagementEvent.OpenDescriptionTags ->
                mutableEffects.tryEmit(
                    TagManagementEffect.NavigateToCategory(TagCategory.DESCRIPTION),
                )
            TagManagementEvent.OpenHardwareSoftwarePurchaseTags ->
                mutableEffects.tryEmit(
                    TagManagementEffect.NavigateToCategory(
                        TagCategory.HARDWARE_SOFTWARE_PURCHASE,
                    ),
                )
        }
    }

    private fun observeCounts() {
        observationJob?.cancel()
        observationJob =
            combine(
                tagRepository.observeTags(TagCategory.DESCRIPTION),
                tagRepository.observeTags(TagCategory.HARDWARE_SOFTWARE_PURCHASE),
            ) { descriptions, purchases ->
                descriptions.size to purchases.size
            }.onStart {
                mutableUiState.update { it.copy(isLoading = true, hasLoadError = false) }
            }.onEach { (descriptionCount, purchaseCount) ->
                mutableUiState.update {
                    it.copy(
                        isLoading = false,
                        hasLoadError = false,
                        descriptionTagCount = descriptionCount,
                        purchaseTagCount = purchaseCount,
                    )
                }
            }.catch {
                mutableUiState.update { it.copy(isLoading = false, hasLoadError = true) }
            }.launchIn(viewModelScope)
    }

    class Factory(
        private val tagRepository: TagRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(TagManagementViewModel::class.java))
            return TagManagementViewModel(tagRepository) as T
        }
    }
}
