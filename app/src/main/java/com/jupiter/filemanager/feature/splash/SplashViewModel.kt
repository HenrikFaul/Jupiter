package com.jupiter.filemanager.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.data.permission.StorageAccessState
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import com.jupiter.filemanager.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Decides where the application should route after the brand splash screen.
 *
 * The decision is made once on init by reading whether onboarding has completed
 * and the current storage-access state:
 *  - onboarding not completed -> [Destination.Onboarding]
 *  - onboarding completed but no storage access -> [Destination.Permission]
 *  - otherwise -> [Destination.Main]
 *
 * The resolved route is published via [targetRoute] (null until resolved) so the
 * UI can wait for a non-null value before navigating exactly once.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val appState: AppStateDataStore,
    private val storageAccessManager: StorageAccessManager,
) : ViewModel() {

    private val _targetRoute = MutableStateFlow<String?>(null)

    /** The destination route to navigate to, or null while it is being resolved. */
    val targetRoute: StateFlow<String?> = _targetRoute.asStateFlow()

    init {
        resolveRoute()
    }

    private fun resolveRoute() {
        viewModelScope.launch {
            val onboardingCompleted = appState.onboardingCompleted.first()
            val route = when {
                !onboardingCompleted -> Destination.Onboarding.route
                storageAccessManager.currentState() == StorageAccessState.NONE ->
                    Destination.Permission.route
                else -> Destination.Main.route
            }
            _targetRoute.value = route
        }
    }
}
