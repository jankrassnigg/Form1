package com.form1.musicplayer.media

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for file browser screen
 * Manages loading and filtering of audio files
 */
class FileBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaScanner = MediaScanner(application.applicationContext)

    private val _uiState = MutableStateFlow<FileBrowserUiState>(FileBrowserUiState.Loading)
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var allFiles: List<AudioFile> = emptyList()
    private var currentSortOrder = SortOrder.TITLE_ASC

    init {
        loadAudioFiles()
    }

    /**
     * Load all audio files from device storage
     */
    fun loadAudioFiles() {
        _uiState.value = FileBrowserUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                allFiles = mediaScanner.scanAudioFiles()

                if (allFiles.isEmpty()) {
                    _uiState.value = FileBrowserUiState.Empty
                } else {
                    _uiState.value = FileBrowserUiState.Success(
                        files = sortFiles(allFiles, currentSortOrder),
                        sortOrder = currentSortOrder
                    )
                }
            } catch (e: Exception) {
                _uiState.value = FileBrowserUiState.Error(
                    message = e.message ?: "Failed to load audio files"
                )
            }
        }
    }

    /**
     * Filter files by search query
     */
    fun search(query: String) {
        if (query.isBlank()) {
            // Show all files when search is cleared
            _uiState.value = FileBrowserUiState.Success(
                files = sortFiles(allFiles, currentSortOrder),
                sortOrder = currentSortOrder
            )
            return
        }

        val filtered = allFiles.filter { file ->
            file.title.contains(query, ignoreCase = true) ||
            file.artist.contains(query, ignoreCase = true) ||
            file.album.contains(query, ignoreCase = true) ||
            file.displayName.contains(query, ignoreCase = true)
        }

        _uiState.value = if (filtered.isEmpty()) {
            FileBrowserUiState.Empty
        } else {
            FileBrowserUiState.Success(
                files = sortFiles(filtered, currentSortOrder),
                sortOrder = currentSortOrder
            )
        }
    }

    /**
     * Change sort order
     */
    fun setSortOrder(sortOrder: SortOrder) {
        currentSortOrder = sortOrder

        val currentState = _uiState.value
        if (currentState is FileBrowserUiState.Success) {
            _uiState.value = currentState.copy(
                files = sortFiles(currentState.files, sortOrder),
                sortOrder = sortOrder
            )
        }
    }

    /**
     * Sort files by specified order
     */
    private fun sortFiles(files: List<AudioFile>, sortOrder: SortOrder): List<AudioFile> {
        return when (sortOrder) {
            SortOrder.TITLE_ASC -> files.sortedBy { it.title.lowercase() }
            SortOrder.TITLE_DESC -> files.sortedByDescending { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> files.sortedBy { it.artist.lowercase() }
            SortOrder.ARTIST_DESC -> files.sortedByDescending { it.artist.lowercase() }
            SortOrder.DURATION_ASC -> files.sortedBy { it.duration }
            SortOrder.DURATION_DESC -> files.sortedByDescending { it.duration }
            SortOrder.DATE_MODIFIED_ASC -> files.sortedBy { it.dateModified }
            SortOrder.DATE_MODIFIED_DESC -> files.sortedByDescending { it.dateModified }
        }
    }
}

/**
 * UI state for file browser
 */
sealed class FileBrowserUiState {
    object Loading : FileBrowserUiState()
    object Empty : FileBrowserUiState()
    data class Success(
        val files: List<AudioFile>,
        val sortOrder: SortOrder
    ) : FileBrowserUiState()
    data class Error(val message: String) : FileBrowserUiState()
}

/**
 * Sort order options
 */
enum class SortOrder(val displayName: String) {
    TITLE_ASC("Title (A-Z)"),
    TITLE_DESC("Title (Z-A)"),
    ARTIST_ASC("Artist (A-Z)"),
    ARTIST_DESC("Artist (Z-A)"),
    DURATION_ASC("Duration (Short to Long)"),
    DURATION_DESC("Duration (Long to Short)"),
    DATE_MODIFIED_ASC("Date (Oldest First)"),
    DATE_MODIFIED_DESC("Date (Newest First)")
}
