package com.jupiter.filemanager.feature.workspace

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.getOrNull
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.Workspace
import com.jupiter.filemanager.domain.repository.FileRepository
import com.jupiter.filemanager.domain.repository.WorkspaceRepository
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Hosts the state for [WorkspaceDetailScreen].
 *
 * The workspace identifier is supplied via the navigation argument keyed by
 * [Destination.WorkspaceDetail.ARG_ID] in [SavedStateHandle]. On load the
 * workspace is fetched from [workspaceRepository] and each of its member paths
 * is resolved through [fileRepository]; unresolved paths are reported as
 * missing so the UI can show an honest, accurate listing.
 */
@HiltViewModel
class WorkspaceDetailViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val fileRepository: FileRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val workspaceId: String? =
        savedStateHandle.get<String>(Destination.WorkspaceDetail.ARG_ID)

    private val _uiState = MutableStateFlow(WorkspaceDetailUiState())
    val uiState: StateFlow<WorkspaceDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Resolves the workspace and the file items behind each of its member paths. */
    fun load() {
        val id = workspaceId
        if (id.isNullOrBlank()) {
            _uiState.update {
                it.copy(isLoading = false, notFound = true)
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val workspace: Workspace? = workspaceRepository.getWorkspace(id)
            if (workspace == null) {
                _uiState.update {
                    it.copy(isLoading = false, workspace = null, notFound = true)
                }
                return@launch
            }

            val resolved = mutableListOf<FileItem>()
            val missing = mutableListOf<String>()
            for (path in workspace.itemPaths) {
                val item = fileRepository.getFile(path).getOrNull()
                if (item != null) {
                    resolved += item
                } else {
                    missing += path
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    workspace = workspace,
                    items = resolved,
                    missingPaths = missing,
                    notFound = false,
                    errorMessage = null,
                )
            }
        }
    }

    /**
     * Permanently deletes this workspace. The collection metadata is removed;
     * the underlying files on disk are never touched. [onDeleted] is invoked on
     * completion so the caller can navigate away.
     */
    fun deleteWorkspace(onDeleted: () -> Unit) {
        val id = _uiState.value.workspace?.id ?: workspaceId ?: return
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(id)
            onDeleted()
        }
    }
}
