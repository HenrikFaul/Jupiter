package com.jupiter.filemanager.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the onboarding flow.
 *
 * Its sole responsibility is to persist the fact that the user has finished (or
 * skipped) onboarding so the splash router no longer sends them here. Persistence
 * is delegated to [AppStateDataStore.setOnboardingCompleted].
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appState: AppStateDataStore,
) : ViewModel() {

    /**
     * Marks onboarding as completed. Safe to call multiple times; the write is
     * idempotent and runs on the DataStore's IO context.
     */
    fun complete() {
        viewModelScope.launch {
            appState.setOnboardingCompleted(true)
        }
    }
}
