package com.jupiter.filemanager.feature.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.apps.AppStorageSource
import com.jupiter.filemanager.domain.model.AppStorageOverview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the App-storage screen.
 *
 * @property isLoading whether the per-app query is running.
 * @property overview the aggregated per-app storage, or null before the first load.
 * @property permissionRequired true when Usage-access has not been granted.
 */
data class AppStorageUiState(
    val isLoading: Boolean = false,
    val overview: AppStorageOverview? = null,
    val permissionRequired: Boolean = false,
)

/**
 * Drives the App-storage screen: queries [AppStorageSource] for per-app usage and exposes a
 * permission-required state so the screen can prompt for Usage-access. Re-queried on resume
 * (e.g. after the user grants access in system settings).
 */
@HiltViewModel
class AppStorageViewModel @Inject constructor(
    private val source: AppStorageSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppStorageUiState())
    val uiState: StateFlow<AppStorageUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** (Re)loads per-app storage. Safe to call repeatedly (init, retry, on-resume). */
    fun load() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val overview = source.query()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    overview = overview,
                    permissionRequired = overview.permissionRequired,
                )
            }
        }
    }

    /** True when the grant is currently held (used to re-query only when it changed). */
    fun hasUsageAccess(): Boolean = source.hasUsageAccess()
}
