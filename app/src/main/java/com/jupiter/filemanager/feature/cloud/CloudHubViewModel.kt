package com.jupiter.filemanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs [CloudHubScreen]. Streams the user's linked cloud accounts from
 * [ConnectionRepository] and exposes add/remove actions plus add-sheet
 * visibility.
 *
 * Account link entries are persisted so they survive process death, but no live
 * provider authentication backend exists yet; accounts are therefore always
 * surfaced as not-connected with zero usage, and the screen presents honest
 * "Connect" / "Coming soon" affordances.
 */
@HiltViewModel
class CloudHubViewModel @Inject constructor(
    private val connectionRepository: ConnectionRepository,
) : ViewModel() {

    private val showAddSheet = MutableStateFlow(false)

    val uiState: StateFlow<CloudHubUiState> =
        combine(
            connectionRepository.observeCloudAccounts(),
            showAddSheet,
        ) { accounts, showSheet ->
            CloudHubUiState(
                isLoading = false,
                accounts = accounts,
                showAddSheet = showSheet,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CloudHubUiState(isLoading = true),
        )

    /** Reveals the "Add cloud account" bottom sheet. */
    fun onAddRequested() {
        showAddSheet.value = true
    }

    /** Hides the "Add cloud account" bottom sheet without linking anything. */
    fun onDismissAddSheet() {
        showAddSheet.value = false
    }

    /**
     * Links a new cloud account for the given [provider] under [displayName]
     * (trimmed). Blank names are ignored. Closes the add sheet on submit.
     */
    fun onAddAccount(provider: CloudProvider, displayName: String) {
        val name = displayName.trim()
        showAddSheet.value = false
        if (name.isEmpty()) return
        viewModelScope.launch {
            connectionRepository.addCloudAccount(provider, name)
        }
    }

    /** Removes the linked cloud account identified by [id]. */
    fun onRemoveAccount(id: String) {
        viewModelScope.launch {
            connectionRepository.removeCloudAccount(id)
        }
    }
}
