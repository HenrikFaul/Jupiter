package com.jupiter.filemanager.core.entitlement

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Premium-capable features that may be gated behind a [Tier].
 *
 * Gating is intentionally inert by default: because [SettingsDataStore.proUnlocked]
 * defaults to `true`, every feature reports as unlocked until a real billing product
 * is configured, so no existing capability is regressed.
 */
enum class Feature {
    VAULT,
    REMOTE,
    AI,
    DUAL_PANE,
    ADVANCED_CLEANUP,
    THEMES,
    ANALYTICS_FREE,
}

/** Entitlement tier the current user belongs to. */
enum class Tier {
    FREE,
    PRO,
}

/**
 * Single source of truth for whether the user is entitled to Pro capabilities.
 *
 * Backed entirely by [SettingsDataStore.proUnlocked], which defaults to `true`,
 * so [tier] is [Tier.PRO] and [isUnlocked] is `true` for every [Feature] until a
 * Play Billing product flips the preference. This guarantees the no-regression
 * law: nothing is locked by default.
 */
@Singleton
class EntitlementManager @Inject constructor(
    private val settings: SettingsDataStore,
) {

    /** Emits [Tier.PRO] when Pro is unlocked, otherwise [Tier.FREE]. */
    fun tier(): Flow<Tier> = settings.proUnlocked.map { unlocked ->
        if (unlocked) Tier.PRO else Tier.FREE
    }

    /**
     * Emits whether [feature] is currently unlocked.
     *
     * Returns `true` whenever Pro is unlocked (the default), so existing features
     * are never blocked. The [feature] parameter is accepted for call-site clarity
     * and future per-feature gating without changing this signature.
     */
    fun isUnlocked(feature: Feature): Flow<Boolean> = settings.proUnlocked
}
