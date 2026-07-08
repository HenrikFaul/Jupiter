package com.jupiter.filemanager.feature.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.entitlement.EntitlementManager
import com.jupiter.filemanager.core.entitlement.Tier
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the Jupiter Pro paywall.
 *
 * The paywall is intentionally honest: no Play Billing product is wired up yet,
 * so [purchaseAvailable] is always `false` and the screen never claims a purchase
 * occurred. Because [SettingsDataStore.proUnlocked] defaults to `true`, [proUnlocked]
 * is normally `true` and every feature is already free — the screen communicates
 * this rather than pretending the user must pay.
 */
data class BillingUiState(
    /** Whether Pro is currently unlocked (defaults to true; nothing is gated). */
    val proUnlocked: Boolean = true,
    /** Current entitlement tier derived from [proUnlocked]. */
    val tier: Tier = Tier.PRO,
    /**
     * Whether a real, purchasable Play Billing product is available. Always `false`
     * for now; the "Go Pro" button shows a coming-soon state instead of charging.
     */
    val purchaseAvailable: Boolean = false,
)

/**
 * Backs [PaywallScreen]. Surfaces the current entitlement state and exposes a
 * developer-only toggle to flip [SettingsDataStore.proUnlocked] so Pro gating can
 * be exercised before a billing product exists.
 *
 * No actual purchase, restore, or network billing flow is performed here: there is
 * no Play Billing product configured, and all Jupiter features are currently free.
 */
@HiltViewModel
class BillingViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val entitlement: EntitlementManager,
) : ViewModel() {

    val uiState: StateFlow<BillingUiState> = settings.proUnlocked
        .map { unlocked ->
            BillingUiState(
                proUnlocked = unlocked,
                tier = if (unlocked) Tier.PRO else Tier.FREE,
                purchaseAvailable = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = BillingUiState(),
        )

    /**
     * Developer-only override that sets [SettingsDataStore.proUnlocked]. Intended
     * for testing entitlement gating until Play Billing is configured; this does
     * not represent a real purchase.
     */
    fun setProUnlocked(value: Boolean) {
        viewModelScope.launch {
            settings.setProUnlocked(value)
        }
    }
}
