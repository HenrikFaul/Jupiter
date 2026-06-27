package com.jupiter.filemanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [NasConnectionsScreen]. Streams the user's configured remote/network
 * connections from [ConnectionRepository] and exposes add/remove actions plus
 * add-dialog visibility.
 *
 * Connection definitions are persisted so they survive process death, but no
 * live protocol I/O backend (SMB / SFTP / FTP / WebDAV / NAS) exists yet, so
 * every connection is surfaced honestly as offline and the screen presents
 * "Connect" / "Coming soon" affordances rather than fabricating reachability.
 */
@HiltViewModel
class NasConnectionsViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val showAddDialog = MutableStateFlow(false)

    val uiState: StateFlow<NasConnectionsUiState> =
        combine(
            connectionRepository.observeRemotes()
                .map { remotes -> remotes.sortedBy { it.displayName.lowercase() } },
            showAddDialog,
        ) { remotes, showDialog ->
            NasConnectionsUiState(
                isLoading = false,
                connections = remotes,
                showAddDialog = showDialog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NasConnectionsUiState(isLoading = true),
        )

    /** Reveals the "Add connection" dialog. */
    fun onAddRequested() {
        showAddDialog.value = true
    }

    /** Hides the "Add connection" dialog without persisting anything. */
    fun onDismissAddDialog() {
        showAddDialog.value = false
    }

    /**
     * Persists a new remote connection definition.
     *
     * The [displayName], [host] and [username] are trimmed; a blank username is
     * stored as null. Submissions with a blank display name or host are ignored.
     * The dialog is closed regardless so the form does not linger.
     */
    fun onAddConnection(
        displayName: String,
        type: ConnectionType,
        host: String,
        username: String,
    ) {
        showAddDialog.value = false
        val name = displayName.trim()
        val trimmedHost = host.trim()
        if (name.isEmpty() || trimmedHost.isEmpty()) return
        val trimmedUser = username.trim().ifEmpty { null }
        viewModelScope.launch {
            connectionRepository.addRemote(
                displayName = name,
                type = type,
                host = trimmedHost,
                username = trimmedUser,
            )
        }
    }

    /** Removes the remote connection identified by [id]. */
    fun onRemoveConnection(id: String) {
        viewModelScope.launch {
            connectionRepository.removeRemote(id)
        }
    }
}
