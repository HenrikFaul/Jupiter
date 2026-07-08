package com.jupiter.filemanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import com.jupiter.filemanager.domain.repository.RemoteAccessRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [NasConnectionsScreen]. Streams the user's configured remote/network
 * connections from [ConnectionRepository] and exposes add/remove actions plus
 * add-dialog visibility.
 *
 * Adding a connection now performs a real reachability test through
 * [RemoteAccessRepository.testConnection] before persisting. While the test is
 * in flight the dialog stays open and shows a progress indicator; on success the
 * connection is saved (with its password stored encrypted by the repository) and
 * the dialog closes; on failure the error is surfaced and the dialog stays open.
 */
@HiltViewModel
class NasConnectionsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
    private val remoteAccessRepository: RemoteAccessRepository,
) : ViewModel() {

    /** Holds the transient add-dialog state (visibility, in-flight test, error). */
    private data class DialogState(
        val visible: Boolean = false,
        val testing: Boolean = false,
        val error: String? = null,
    )

    private val dialogState = MutableStateFlow(DialogState())

    val uiState: StateFlow<NasConnectionsUiState> =
        combine(
            connectionRepository.observeRemotes()
                .map { remotes -> remotes.sortedBy { it.displayName.lowercase() } },
            dialogState,
        ) { remotes, dialog ->
            NasConnectionsUiState(
                isLoading = false,
                connections = remotes,
                showAddDialog = dialog.visible,
                isTesting = dialog.testing,
                testError = dialog.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NasConnectionsUiState(isLoading = true),
        )

    /** Reveals the "Add connection" dialog, clearing any stale error. */
    fun onAddRequested() {
        dialogState.value = DialogState(visible = true)
    }

    /** Hides the "Add connection" dialog without persisting anything. */
    fun onDismissAddDialog() {
        dialogState.value = DialogState(visible = false)
    }

    /**
     * Tests then persists a new remote connection definition.
     *
     * All text fields are trimmed; blank username/password/basePath become null.
     * A blank display name or host aborts silently. The supplied [port] of 0 means
     * "use the protocol default" and is forwarded unchanged. The reachability test
     * runs first via [RemoteAccessRepository.testConnection]; only on success is
     * the connection saved (the password is stored encrypted by the repository,
     * never inside the connection entry). On failure the dialog stays open with
     * [NasConnectionsUiState.testError] populated.
     */
    fun addConnection(
        displayName: String,
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ) {
        val name = displayName.trim()
        val trimmedHost = host.trim()
        if (name.isEmpty() || trimmedHost.isEmpty()) return

        val trimmedUser = username?.trim()?.ifEmpty { null }
        val trimmedPassword = password?.trim()?.ifEmpty { null }
        val trimmedBasePath = basePath?.trim()?.ifEmpty { null }

        dialogState.update { it.copy(visible = true, testing = true, error = null) }

        viewModelScope.launch {
            when (
                val result = remoteAccessRepository.testConnection(
                    type = type,
                    host = trimmedHost,
                    port = port,
                    username = trimmedUser,
                    password = trimmedPassword,
                    basePath = trimmedBasePath,
                )
            ) {
                is AppResult.Success -> {
                    connectionRepository.addRemote(
                        displayName = name,
                        type = type,
                        host = trimmedHost,
                        port = port,
                        username = trimmedUser,
                        password = trimmedPassword,
                        basePath = trimmedBasePath,
                    )
                    dialogState.value = DialogState(visible = false)
                }

                is AppResult.Failure -> {
                    dialogState.update {
                        it.copy(
                            visible = true,
                            testing = false,
                            error = result.error.displayMessage,
                        )
                    }
                }
            }
        }
    }

    /** Removes the remote connection identified by [id]. */
    fun onRemoveConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.removeRemote(id)
        }
    }
}
