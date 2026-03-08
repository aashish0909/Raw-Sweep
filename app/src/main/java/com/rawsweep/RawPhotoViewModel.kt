package com.rawsweep

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RawPhotoUiState(
    val isLoading: Boolean = false,
    val photos: List<RawPhoto> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val error: Throwable? = null
) {
    val selectedCount: Int
        get() = selectedIds.size
}

class RawPhotoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RawPhotoRepository(application.contentResolver)
    private val _uiState = MutableStateFlow(RawPhotoUiState(isLoading = true))
    val uiState: StateFlow<RawPhotoUiState> = _uiState

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) { repository.loadRawPhotos() }
            }.onSuccess { photos ->
                _uiState.update { state ->
                    val existingSelection = state.selectedIds.intersect(photos.map { it.id }.toSet())
                    state.copy(
                        isLoading = false,
                        photos = photos,
                        selectedIds = existingSelection,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error) }
            }
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { state ->
            val updated = state.selectedIds.toMutableSet()
            if (!updated.add(photoId)) {
                updated.remove(photoId)
            }
            state.copy(selectedIds = updated)
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun buildDeleteIntentSender(): IntentSender? {
        val state = _uiState.value
        val targetUris = state.photos
            .asSequence()
            .filter { it.id in state.selectedIds }
            .map { it.uri }
            .toList()
        return repository.createDeleteRequest(targetUris)
    }

    fun onDeleteConfirmed() {
        clearSelection()
        refresh()
    }
}
