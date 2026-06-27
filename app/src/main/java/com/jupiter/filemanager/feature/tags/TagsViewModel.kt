package com.jupiter.filemanager.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.getOrNull
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the Tags screen: streams user-defined tags from [TagRepository], lets the
 * user create new tags, and resolves the files associated with a selected tag.
 */
@HiltViewModel
class TagsViewModel @Inject constructor(
    private val tagRepository: TagRepository,
    private val fileRepository: com.jupiter.filemanager.domain.repository.FileRepository,
) : ViewModel() {

    private val selectedTagId = MutableStateFlow<String?>(null)
    private val filesForSelectedTag = MutableStateFlow<List<FileItem>>(emptyList())
    private val isFilesLoading = MutableStateFlow(false)

    /** Tracks the in-flight file-resolution collection so switching tags cancels it. */
    private var filesJob: Job? = null

    val uiState: StateFlow<TagsUiState> = combine(
        tagRepository.observeTags(),
        selectedTagId,
        filesForSelectedTag,
        isFilesLoading,
    ) { tags, selectedId, files, filesLoading ->
        TagsUiState(
            tags = tags,
            selectedTagId = selectedId,
            selectedTagName = tags.firstOrNull { it.id == selectedId }?.name,
            filesForSelectedTag = files,
            isLoading = false,
            isFilesLoading = filesLoading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TagsUiState(isLoading = true),
    )

    /** Creates a new tag with the given [name] and packed ARGB [colorArgb]. */
    fun addTag(name: String, colorArgb: Long) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            tagRepository.addTag(trimmed, colorArgb)
        }
    }

    /** Permanently removes the tag identified by [id]. */
    fun removeTag(id: String) {
        viewModelScope.launch {
            tagRepository.removeTag(id)
            if (selectedTagId.value == id) {
                clearSelection()
            }
        }
    }

    /** Selects [tagId] and begins resolving its associated files. */
    fun selectTag(tagId: String) {
        selectedTagId.value = tagId
        filesForSelectedTag.value = emptyList()
        isFilesLoading.value = true
        filesJob?.cancel()
        filesJob = viewModelScope.launch {
            tagRepository.filesForTag(tagId).collectLatest { paths ->
                val resolved = paths.mapNotNull { path ->
                    fileRepository.getFile(path).getOrNull()
                }
                filesForSelectedTag.value = resolved
                isFilesLoading.value = false
            }
        }
    }

    /** Clears the current tag selection, returning to the tag list. */
    fun clearSelection() {
        filesJob?.cancel()
        filesJob = null
        selectedTagId.value = null
        filesForSelectedTag.value = emptyList()
        isFilesLoading.value = false
    }
}
