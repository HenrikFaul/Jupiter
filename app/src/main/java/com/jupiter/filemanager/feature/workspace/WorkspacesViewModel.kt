package com.jupiter.filemanager.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [WorkspacesScreen]. Streams the user's saved workspaces from
 * [WorkspaceRepository] and exposes create/delete actions.
 */
@HiltViewModel
class WorkspacesViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    private data class DialogState(
        val show: Boolean = false,
        val creating: Boolean = false,
    )

    private val dialogState = MutableStateFlow(DialogState())

    val uiState: StateFlow<WorkspacesUiState> =
        combine(
            workspaceRepository.observeWorkspaces(),
            dialogState,
        ) { workspaces, dialog ->
            WorkspacesUiState(
                isLoading = false,
                workspaces = workspaces.sortedByDescending { it.lastModified },
                showCreateDialog = dialog.show,
                isCreating = dialog.creating,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkspacesUiState(isLoading = true),
        )

    /** Shows the create-workspace dialog. */
    fun onCreateRequested() {
        dialogState.value = dialogState.value.copy(show = true)
    }

    /** Dismisses the create-workspace dialog without creating anything. */
    fun onDismissCreateDialog() {
        if (dialogState.value.creating) return
        dialogState.value = DialogState()
    }

    /**
     * Creates a new, empty workspace named [name] (trimmed). No-op for blank names.
     */
    fun onCreateWorkspace(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || dialogState.value.creating) return
        dialogState.value = dialogState.value.copy(creating = true)
        viewModelScope.launch {
            try {
                workspaceRepository.createWorkspace(trimmed, emptyList())
            } finally {
                dialogState.value = DialogState()
            }
        }
    }

    /** Permanently deletes the workspace identified by [id]. */
    fun onDeleteWorkspace(id: String) {
        viewModelScope.launch {
            workspaceRepository.deleteWorkspace(id)
        }
    }
}
