package com.rawsweep.viewmodel

import android.app.Application
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rawsweep.data.RawPhoto
import com.rawsweep.data.RawPhotoRepository
import com.rawsweep.data.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GalleryUiState(
    val photos: List<RawPhoto> = emptyList(),
    val selectedIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isSelectionMode: Boolean = false,
    val sortOption: SortOption = SortOption.DATE_NEWEST,
    val totalRawSize: Long = 0L,
    val deleteRequestUris: List<Uri>? = null,
    val snackbarMessage: String? = null,
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RawPhotoRepository(application.contentResolver)
    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var allPhotos: List<RawPhoto> = emptyList()
    private var initialLoadDone = false

    fun loadPhotosIfNeeded() {
        if (!initialLoadDone) {
            initialLoadDone = true
            loadPhotos()
        }
    }

    fun resetLoadState() {
        initialLoadDone = false
    }

    fun loadPhotos() {
        initialLoadDone = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                allPhotos = repository.loadRawPhotos()
                val sorted = sortPhotos(allPhotos, _uiState.value.sortOption)
                val totalSize = allPhotos.sumOf { it.size }
                _uiState.update {
                    it.copy(
                        photos = sorted,
                        isLoading = false,
                        totalRawSize = totalSize,
                        selectedIds = emptySet(),
                        isSelectionMode = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        snackbarMessage = "Failed to load photos: ${e.message}"
                    )
                }
            }
        }
    }

    fun toggleSelection(photoId: Long) {
        _uiState.update { state ->
            val newSelected = state.selectedIds.toMutableSet()
            if (newSelected.contains(photoId)) {
                newSelected.remove(photoId)
            } else {
                newSelected.add(photoId)
            }
            state.copy(
                selectedIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty(),
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedIds = state.photos.map { it.id }.toSet(),
                isSelectionMode = true,
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(selectedIds = emptySet(), isSelectionMode = false)
        }
    }

    fun setSortOption(option: SortOption) {
        _uiState.update { state ->
            val sorted = sortPhotos(allPhotos, option)
            state.copy(photos = sorted, sortOption = option)
        }
    }

    fun requestDeleteSelected() {
        val state = _uiState.value
        val urisToDelete = state.photos
            .filter { state.selectedIds.contains(it.id) }
            .map { it.uri }

        if (urisToDelete.isEmpty()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            _uiState.update { it.copy(deleteRequestUris = urisToDelete) }
        } else {
            viewModelScope.launch {
                val deleted = repository.deletePhotosLegacy(urisToDelete)
                _uiState.update {
                    it.copy(snackbarMessage = "Deleted $deleted photo(s)")
                }
                loadPhotos()
            }
        }
    }

    fun createDeleteIntentSender(uris: List<Uri>): android.content.IntentSender? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = MediaStore.createDeleteRequest(
                getApplication<Application>().contentResolver,
                uris
            )
            return intent.intentSender
        }
        return null
    }

    fun onDeleteRequestHandled() {
        _uiState.update { it.copy(deleteRequestUris = null) }
    }

    fun onDeleteCompleted(deletedCount: Int) {
        _uiState.update {
            it.copy(snackbarMessage = "Deleted $deletedCount photo(s)")
        }
        loadPhotos()
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun getSelectedSize(): Long {
        val state = _uiState.value
        return state.photos
            .filter { state.selectedIds.contains(it.id) }
            .sumOf { it.size }
    }

    private fun sortPhotos(photos: List<RawPhoto>, option: SortOption): List<RawPhoto> {
        return when (option) {
            SortOption.DATE_NEWEST -> photos.sortedByDescending { it.dateAdded }
            SortOption.DATE_OLDEST -> photos.sortedBy { it.dateAdded }
            SortOption.SIZE_LARGEST -> photos.sortedByDescending { it.size }
            SortOption.SIZE_SMALLEST -> photos.sortedBy { it.size }
            SortOption.NAME_AZ -> photos.sortedBy { it.displayName.lowercase() }
            SortOption.NAME_ZA -> photos.sortedByDescending { it.displayName.lowercase() }
        }
    }
}
