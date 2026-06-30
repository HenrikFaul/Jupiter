# Initiative #1 — Jupiter Pro: Monetization & Entitlement Engine

> Dev-prompt for an autonomous AI coding agent. Implement end-to-end, **additively**, against the **real** Jupiter codebase at `/home/user/Jupiter`. Read every referenced real file before editing. Produce compiling Kotlin only — no pseudocode, no TODO stubs that break the build.

---

## 1. Initiative header

- **Title:** Jupiter Pro — Monetization & Entitlement Engine
- **Estimated valuation impact:** **+€350k – €700k**
- **Business case:** Jupiter today is a feature-complete, ad-free file manager with zero monetization surface, which caps its enterprise/acquisition valuation at "nice app" rather than "revenue-generating product." Introducing a freemium **Pro tier** backed by **Google Play Billing** is the single highest-leverage change: it converts existing, already-shipped premium-grade surfaces (encrypted Vault, NAS/SMB/SFTP/FTP/WebDAV remote access, the AI Suite, the dual-pane browser, and advanced cleanup) into paid value without building new user-facing features. A central `EntitlementManager` gates those surfaces behind a `Feature` enum and a `Tier`, while a **generous, fail-open free tier** guarantees that nothing currently working regresses: until a *real* billing product is configured and a purchase is verified, every feature stays unlocked. This protects the existing demo/CI experience and lets monetization roll out behind a flag, while establishing the entitlement plumbing that initiatives #2–#10 (cloud quotas, AI credits, team licensing) build upon.

---

## 2. Codebase context

Module: single app module `:app`, package root `com.jupiter.filemanager`, Kotlin 2.0.21, Compose BOM 2024.12.01, Hilt 2.52, KSP, Coroutines 1.9.0, DataStore Preferences 1.1.1, minSdk 26 / targetSdk 35 / compileSdk 35.

### Relevant real files that already exist (read these first)

```
/home/user/Jupiter
├── gradle/libs.versions.toml                                  # version catalog (edit)
├── app/build.gradle.kts                                       # module deps (edit)
├── app/proguard-rules.pro                                     # R8 keep rules (edit)
├── app/src/main/AndroidManifest.xml                           # no edit needed (BILLING auto-merged)
└── app/src/main/java/com/jupiter/filemanager
    ├── JupiterApp.kt                                           # @HiltAndroidApp Application
    ├── MainActivity.kt                                         # ComponentActivity (host for billing)
    ├── core/result/AppResult.kt                               # AppResult.Success/Failure (reuse)
    ├── core/result/AppError.kt                                # AppError sealed class (extend)
    ├── di/CoroutineModule.kt                                  # @IoDispatcher qualifier (reuse)
    ├── di/RepositoryModule.kt                                 # @Binds module pattern (mirror)
    ├── data/preferences/SettingsDataStore.kt                 # DataStore pattern (mirror)
    ├── ui/navigation/Destinations.kt                          # sealed Destination (edit: add Paywall)
    ├── ui/navigation/JupiterNavHost.kt                        # NavHost graph (edit: wire Paywall)
    ├── feature/main/MainScreen.kt                             # exposes onOpenRoute (entry to paywall)
    ├── feature/vault/VaultViewModel.kt                        # gate entry point
    ├── feature/vault/VaultScreen.kt                           # gate entry point
    ├── feature/cloud/CloudHubScreen.kt                        # gate entry point (NAS/cloud)
    ├── feature/cloud/NasConnectionsScreen.kt                 # gate entry point
    ├── feature/ai/AiAssistantScreen.kt                        # gate entry point (AI Suite)
    └── feature/browser/DualPaneScreen.kt                      # gate entry point (dual-pane)
```

### What exists vs. missing vs. needs change

| Concern | State | Action |
|---|---|---|
| `AppResult` / `AppError` result types | **Exists** | Reuse; add 2 `AppError` billing variants |
| `@IoDispatcher` qualifier | **Exists** (`di/CoroutineModule.kt`) | Reuse for all suspend/IO work |
| DataStore persistence pattern | **Exists** (`SettingsDataStore.kt`) | Mirror for entitlement cache |
| Hilt `@Binds` module pattern | **Exists** (`di/RepositoryModule.kt`) | Mirror for new bindings |
| `core/entitlement` package | **Missing** | **Create**: `Feature`, `Tier`, `EntitlementManager`, impl |
| `feature/billing` package | **Missing** | **Create**: `BillingClientWrapper`, `BillingRepository`, `UpgradeViewModel`, `PaywallScreen`, `FeatureGate` composable |
| Play Billing dependency | **Missing** | **Add** `billing-ktx` to catalog + module |
| `Destination.Paywall` + nav wiring | **Missing** | **Add** to `Destinations.kt` + `JupiterNavHost.kt` |
| Gate checks at premium entry points | **Missing** | **Add** lightweight, additive checks (no behavior change while unlocked) |

**Non-negotiable invariant:** the entitlement engine defaults to **`Tier.PRO` (everything unlocked)** until a real, non-placeholder Play product is configured (`BillingConfig.isBillingConfigured == false`). This guarantees zero regression in CI, demos, and current users.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

- `com.android.billingclient:billing-ktx:7.1.1` (Play Billing Library v7, Kotlin extensions; compatible with AGP 8.7.3 / compileSdk 35).

No other new dependencies. DataStore, Hilt, Coroutines, Compose, Navigation are already present.

### Manifest / permission prerequisites

- The Play Billing Library **auto-merges** `<uses-permission android:name="com.android.vending.BILLING"/>` from its own manifest — **do not** add it manually (duplicate-permission lint). No manifest edit is required for this initiative.
- No new runtime permissions. No new `<queries>` entries.

### Play Console prerequisites (deferred — gated by a flag so the build never depends on them)

1. App uploaded to a Play Console track (internal testing is enough).
2. A **subscription** product `jupiter_pro_monthly` and/or an in-app product `jupiter_pro_lifetime` created and **activated**.
3. License testers added under *Setup → License testing*.
4. Set `BillingConfig.isBillingConfigured = true` and fill `PRODUCT_ID_*` only once the products are live (see Phase 4). Until then, the app ships in **fail-open Pro mode**.

### API keys

None. Play Billing uses the signed APK + Play Store account; there is no secret key embedded in the app. Purchase verification is performed locally via the BillingClient + an optional server hook (Phase 4, out of scope to host now).

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Under `[versions]`, add:

```toml
billing = "7.1.1"
```

Under `[libraries]`, add:

```toml
billing-ktx = { group = "com.android.billingclient", name = "billing-ktx", version.ref = "billing" }
```

### 4.2 `app/build.gradle.kts`

In the `dependencies { }` block, after the coroutines line (`implementation(libs.kotlinx.coroutines.android)`), add:

```kotlin
    // Monetization / Google Play Billing (Pro tier entitlement)
    implementation(libs.billing.ktx)
```

No other Gradle change. `buildConfig = true` is already enabled, so `BuildConfig` is available if needed.

### 4.3 Manifest

No change (see Phase 3 pre-conditions). `MainActivity` already exists and is the launch activity; the BillingClient binds to it via `@ApplicationContext` so no activity change is required for the connection. The purchase **launch** call needs an `Activity`; we obtain it from the Compose `LocalContext` by unwrapping `ContextWrapper` to an `Activity` (same pattern already used in `VaultScreen.kt`).

### 4.4 Resources

No new XML resources required. All strings are inlined in Compose for this initiative (the codebase already inlines user-facing strings in feature screens, e.g. `VaultScreen.kt`). If you prefer string resources, add them to `res/values/strings.xml`, but this is optional and not required for DoD.

---

## 5. Phase 2 — Data / domain layer

Create package `com.jupiter.filemanager.core.entitlement` and `com.jupiter.filemanager.feature.billing`. All IO/suspend work uses the existing `@IoDispatcher`. All cross-boundary failures use `AppResult` / `AppError`.

### 5.1 `core/entitlement/Feature.kt`

```kotlin
package com.jupiter.filemanager.core.entitlement

/**
 * Premium-gated surfaces of the application.
 *
 * Each [Feature] maps to a user-facing capability that becomes a paid ("Pro")
 * surface once billing is configured. The mapping is intentionally coarse: a
 * single enum entry guards an entire feature area so gate checks stay cheap and
 * the free/Pro boundary is easy to reason about.
 *
 * Adding a value here never regresses existing behavior because
 * [EntitlementManager] fails open (treats everything as unlocked) until a real
 * billing product is configured.
 */
enum class Feature(
    /** Short, stable identifier used for analytics and the paywall deep-link. */
    val id: String,
    /** Human-readable title shown on the paywall. */
    val title: String,
    /** One-line benefit description shown on the paywall. */
    val description: String,
) {
    VAULT(
        id = "vault",
        title = "Encrypted Vault",
        description = "Hide and AES-encrypt sensitive files behind biometric unlock.",
    ),
    REMOTE_ACCESS(
        id = "remote_access",
        title = "NAS & Remote Access",
        description = "Connect to SMB, SFTP, FTP and WebDAV servers and cloud storage.",
    ),
    AI_SUITE(
        id = "ai_suite",
        title = "AI Suite",
        description = "AI-assisted organization, smart search and file insights.",
    ),
    DUAL_PANE(
        id = "dual_pane",
        title = "Dual-Pane Browser",
        description = "Side-by-side browsing for fast copy and move between folders.",
    ),
    ADVANCED_CLEANUP(
        id = "advanced_cleanup",
        title = "Advanced Cleanup",
        description = "Duplicate detection and smart merge to reclaim storage.",
    ),
}
```

### 5.2 `core/entitlement/Tier.kt`

```kotlin
package com.jupiter.filemanager.core.entitlement

/**
 * Monetization tier the current user belongs to.
 *
 * [FREE] users may use the generous free surface; [PRO] users have every
 * [Feature] unlocked. The default while billing is unconfigured is [PRO]
 * (fail-open), so existing functionality never regresses.
 */
enum class Tier {
    FREE,
    PRO,
}
```

### 5.3 `feature/billing/BillingConfig.kt`

```kotlin
package com.jupiter.filemanager.feature.billing

/**
 * Static, compile-time configuration for the monetization layer.
 *
 * [isBillingConfigured] is the master fail-open switch: while it is `false`
 * the app runs as if every user is Pro, so no premium surface is ever blocked
 * in CI, demos, or installs that predate a real Play product. Flip it to `true`
 * only after the product IDs below exist and are ACTIVE in the Play Console.
 */
object BillingConfig {

    /** Master switch. Keep `false` until real Play products are live. */
    const val isBillingConfigured: Boolean = false

    /** Subscription product id (Play Console → Subscriptions). */
    const val PRODUCT_ID_PRO_SUBSCRIPTION: String = "jupiter_pro_monthly"

    /** One-time in-app product id (Play Console → In-app products). */
    const val PRODUCT_ID_PRO_LIFETIME: String = "jupiter_pro_lifetime"

    /** Base plan tag for the subscription (Play Console subscription base plan). */
    const val SUBSCRIPTION_BASE_PLAN: String = "monthly"
}
```

### 5.4 `core/result/AppError.kt` — additive extension

Add these two variants **inside** the existing `sealed class AppError` (after the `Io` variant, before `Unknown`). Do not remove or reorder existing variants.

```kotlin
    /** The billing service is unavailable or the device cannot make purchases. */
    data class BillingUnavailable(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val displayMessage: String = detail
    }

    /** A purchase flow was cancelled or failed before completing. */
    data class PurchaseFailed(
        val detail: String,
        override val cause: Throwable? = null,
    ) : AppError() {
        override val displayMessage: String = detail
    }
```

### 5.5 `feature/billing/EntitlementCacheDataStore.kt`

Mirrors `data/preferences/SettingsDataStore.kt` exactly (own DataStore file, `safe()` catch, `@Singleton`, `@ApplicationContext`). Persists the last-known Pro state so the app shows the correct tier instantly on cold start before Play reconnects.

```kotlin
package com.jupiter.filemanager.feature.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dedicated [DataStore] backing the cached entitlement state.
 *
 * Declared at file scope (separate from the settings store) so the single
 * process-wide instance is shared, per the DataStore contract.
 */
private val Context.entitlementDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "jupiter_entitlement")

/**
 * Persists the last-known Pro entitlement so the correct [Tier] is available
 * synchronously on cold start, before the Play BillingClient has reconnected
 * and re-queried purchases. Reads fall back to `false` (FREE) on any IO error;
 * the live BillingClient query is authoritative and overwrites this cache.
 */
@Singleton
class EntitlementCacheDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.entitlementDataStore

    private object Keys {
        val IS_PRO = booleanPreferencesKey("is_pro")
    }

    /** Cached Pro flag; defaults to `false` when absent or unreadable. */
    val isProCached: Flow<Boolean> = dataStore.data
        .catch { t -> if (t is IOException) emit(emptyPreferences()) else throw t }
        .map { prefs -> prefs[Keys.IS_PRO] ?: false }

    suspend fun setProCached(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.IS_PRO] = value }
    }
}
```

### 5.6 `feature/billing/BillingClientWrapper.kt`

Thin, lifecycle-safe wrapper around Play `BillingClient`. Owns connection, purchase queries, the purchase launch, and acknowledgement. Exposes a cold `purchasesFlow` of "is the user Pro?" booleans. Never throws across the boundary — connection/query failures emit `false` and are surfaced via `AppResult` from the launch call.

```kotlin
package com.jupiter.filemanager.feature.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wraps the Google Play [BillingClient].
 *
 * Single responsibility: talk to Play, expose whether the user currently owns a
 * Pro entitlement as [isPro], and run the purchase flow. It is intentionally
 * unaware of [com.jupiter.filemanager.core.entitlement.EntitlementManager]; the
 * manager observes [isPro] and combines it with the fail-open config.
 *
 * All Play callbacks are bridged to coroutines on [io]. Any failure resolves to
 * a safe value (`isPro = false` / an [AppResult.Failure]) — never a thrown
 * exception across the boundary.
 */
@Singleton
class BillingClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _isPro = MutableStateFlow(false)
    /** `true` once a verified, acknowledged Pro purchase is observed. */
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            handlePurchases(purchases)
        }
        // USER_CANCELED and error codes are intentionally ignored here; the
        // launchPurchase() suspend call reports those to the caller instead.
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    @Volatile
    private var connected = false

    /** Lazily (re)establishes the Play connection; safe to call repeatedly. */
    private suspend fun ensureConnected(): Boolean = withContext(io) {
        if (connected && client.isReady) return@withContext true
        suspendCancellableCoroutine { cont ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connected = result.responseCode == BillingClient.BillingResponseCode.OK
                    if (cont.isActive) cont.resume(connected)
                }
                override fun onBillingServiceDisconnected() {
                    connected = false
                }
            })
        }
    }

    /**
     * Re-queries owned purchases and updates [isPro]. Call on app start and on
     * resume. No-ops gracefully when Play is unavailable.
     */
    suspend fun refreshPurchases() {
        if (!ensureConnected()) {
            _isPro.value = false
            return
        }
        val subs = queryOwned(BillingClient.ProductType.SUBS)
        val inApp = queryOwned(BillingClient.ProductType.INAPP)
        val all = subs + inApp
        handlePurchases(all)
        if (all.none { it.purchaseState == Purchase.PurchaseState.PURCHASED }) {
            _isPro.value = false
        }
    }

    private suspend fun queryOwned(type: String): List<Purchase> = withContext(io) {
        runCatching {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build(),
            ).purchasesList
        }.getOrDefault(emptyList())
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val owned = purchases.any { p ->
            p.purchaseState == Purchase.PurchaseState.PURCHASED &&
                (BillingConfig.PRODUCT_ID_PRO_SUBSCRIPTION in p.products ||
                    BillingConfig.PRODUCT_ID_PRO_LIFETIME in p.products)
        }
        if (owned) _isPro.value = true
        // Acknowledge unacknowledged, purchased entitlements (Play voids them otherwise).
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }
            .forEach { acknowledge(it) }
    }

    private fun acknowledge(purchase: Purchase) {
        // Fire-and-forget acknowledgement; failure simply means Play retries on next query.
        runCatching {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* result ignored; idempotent */ }
        }
    }

    /**
     * Launches the Play purchase flow for the Pro subscription.
     *
     * @return [AppResult.Success] once the flow was *launched* (the actual grant
     *   arrives asynchronously via [isPro]); [AppResult.Failure] when the device
     *   cannot purchase or the product could not be loaded.
     */
    suspend fun launchPurchase(activity: Activity): AppResult<Unit> = withContext(io) {
        if (!BillingConfig.isBillingConfigured) {
            return@withContext AppResult.Failure(
                AppError.BillingUnavailable("Billing is not configured in this build."),
            )
        }
        if (!ensureConnected()) {
            return@withContext AppResult.Failure(
                AppError.BillingUnavailable("Google Play billing is unavailable."),
            )
        }
        val details = queryProProductDetails()
            ?: return@withContext AppResult.Failure(
                AppError.BillingUnavailable("Pro product is not available right now."),
            )
        val offerToken = details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken
            ?: return@withContext AppResult.Failure(
                AppError.BillingUnavailable("No purchasable offer for Pro."),
            )
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build(),
                ),
            )
            .build()
        val launch: BillingResult = withContext(io) { client.launchBillingFlow(activity, params) }
        if (launch.responseCode == BillingClient.BillingResponseCode.OK) {
            AppResult.Success(Unit)
        } else {
            AppResult.Failure(
                AppError.PurchaseFailed("Could not start purchase (code ${launch.responseCode})."),
            )
        }
    }

    private suspend fun queryProProductDetails(): ProductDetails? = withContext(io) {
        runCatching {
            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(BillingConfig.PRODUCT_ID_PRO_SUBSCRIPTION)
                            .setProductType(BillingClient.ProductType.SUBS)
                            .build(),
                    ),
                )
                .build()
            client.queryProductDetails(params).productDetailsList?.firstOrNull()
        }.getOrNull()
    }
}
```

### 5.7 `core/entitlement/EntitlementManager.kt`

Interface + impl. The impl combines `BillingConfig.isBillingConfigured`, the cached flag, and the live `isPro` flow into a single authoritative `tier` flow. **Fail-open:** when billing is unconfigured, `tier` is always `PRO`.

```kotlin
package com.jupiter.filemanager.core.entitlement

import com.jupiter.filemanager.feature.billing.BillingClientWrapper
import com.jupiter.filemanager.feature.billing.BillingConfig
import com.jupiter.filemanager.feature.billing.EntitlementCacheDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for whether the current user may access a [Feature].
 *
 * Resolution order:
 *  1. If [BillingConfig.isBillingConfigured] is `false` → always [Tier.PRO]
 *     (fail-open; nothing is ever gated). This protects CI/demos and existing users.
 *  2. Otherwise, the user is [Tier.PRO] iff Play reports an owned Pro entitlement
 *     ([BillingClientWrapper.isPro]), backed by the persisted cache for instant
 *     cold-start resolution.
 */
interface EntitlementManager {
    /** Authoritative current tier as a hot [StateFlow]. */
    val tier: StateFlow<Tier>

    /** Convenience: is the given [feature] currently accessible? */
    fun isUnlocked(feature: Feature): Boolean

    /** Re-queries Play for owned purchases (call on app start / resume). */
    suspend fun refresh()
}

@Singleton
class DefaultEntitlementManager @Inject constructor(
    private val billing: BillingClientWrapper,
    private val cache: EntitlementCacheDataStore,
    @com.jupiter.filemanager.di.ApplicationScope private val scope: CoroutineScope,
) : EntitlementManager {

    override val tier: StateFlow<Tier> =
        combine(billing.isPro, cache.isProCached) { live, cached ->
            when {
                !BillingConfig.isBillingConfigured -> Tier.PRO
                live || cached -> Tier.PRO
                else -> Tier.FREE
            }
        }.stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = if (BillingConfig.isBillingConfigured) Tier.FREE else Tier.PRO,
        )

    init {
        // Persist the live Play state so the next cold start resolves instantly.
        scope.launch {
            billing.isPro.collect { isPro -> cache.setProCached(isPro) }
        }
        // Kick off an initial purchase refresh.
        scope.launch { refresh() }
    }

    override fun isUnlocked(feature: Feature): Boolean = tier.value == Tier.PRO

    override suspend fun refresh() {
        if (BillingConfig.isBillingConfigured) billing.refreshPurchases()
    }
}
```

### 5.8 `di/EntitlementModule.kt`

Provides the application `CoroutineScope` (with a new `@ApplicationScope` qualifier) and binds the `EntitlementManager` interface, mirroring the existing `RepositoryModule` `@Binds` style.

```kotlin
package com.jupiter.filemanager.di

import com.jupiter.filemanager.core.entitlement.DefaultEntitlementManager
import com.jupiter.filemanager.core.entitlement.EntitlementManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import javax.inject.Qualifier
import javax.inject.Singleton

/** Marks the long-lived, application-scoped [CoroutineScope]. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object EntitlementProvidesModule {

    /** Application-lifetime scope for entitlement collection. Uses [IoDispatcher]. */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class EntitlementBindsModule {

    @Binds
    @Singleton
    abstract fun bindEntitlementManager(
        impl: DefaultEntitlementManager,
    ): EntitlementManager
}
```

---

## 6. Phase 3 — Presentation

### 6.1 `feature/billing/UpgradeViewModel.kt`

Immutable `UpgradeUiState`, `@HiltViewModel`, drives the paywall. Reports purchase launch failures through `error`. Mirrors `VaultViewModel`'s `StateFlow` + `update {}` style.

```kotlin
package com.jupiter.filemanager.feature.billing

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.entitlement.EntitlementManager
import com.jupiter.filemanager.core.entitlement.Feature
import com.jupiter.filemanager.core.entitlement.Tier
import com.jupiter.filemanager.core.result.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Immutable UI state for the paywall.
 *
 * @property highlightedFeature the feature that triggered the paywall (for copy).
 * @property features all premium features to advertise.
 * @property isPro whether the user is already Pro (then the screen shows a thank-you).
 * @property isPurchasing whether a purchase flow is in flight.
 * @property error a user-facing error message, or null.
 */
data class UpgradeUiState(
    val highlightedFeature: Feature? = null,
    val features: List<Feature> = Feature.entries.toList(),
    val isPro: Boolean = false,
    val isPurchasing: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class UpgradeViewModel @Inject constructor(
    private val billing: BillingClientWrapper,
    private val entitlements: EntitlementManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UpgradeUiState())
    val uiState: StateFlow<UpgradeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            entitlements.tier.collect { tier ->
                _uiState.update { it.copy(isPro = tier == Tier.PRO) }
            }
        }
    }

    /** Sets which feature deep-linked into the paywall, for tailored copy. */
    fun setHighlightedFeature(featureId: String?) {
        val feature = featureId?.let { id -> Feature.entries.firstOrNull { it.id == id } }
        _uiState.update { it.copy(highlightedFeature = feature) }
    }

    /** Starts the Play purchase flow. [activity] must be the hosting Activity. */
    fun purchase(activity: Activity) {
        if (_uiState.value.isPurchasing) return
        _uiState.update { it.copy(isPurchasing = true, error = null) }
        viewModelScope.launch {
            when (val result = billing.launchPurchase(activity)) {
                is AppResult.Success -> _uiState.update { it.copy(isPurchasing = false) }
                is AppResult.Failure -> _uiState.update {
                    it.copy(isPurchasing = false, error = result.error.displayMessage)
                }
            }
        }
    }

    /** Re-checks Play for an already-owned entitlement ("Restore purchases"). */
    fun restore() {
        viewModelScope.launch { entitlements.refresh() }
    }

    fun consumeError() = _uiState.update { it.copy(error = null) }
}
```

### 6.2 `feature/billing/PaywallScreen.kt`

Self-contained Material3 screen. Unwraps the Activity from `LocalContext` exactly as `VaultScreen.kt` does.

```kotlin
package com.jupiter.filemanager.feature.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.entitlement.Feature

/**
 * Paywall / upgrade screen.
 *
 * Lists every premium [Feature], highlights the one that triggered the paywall
 * (if any), and starts the Play purchase flow. When the user is already Pro it
 * shows a thank-you state instead of a purchase button. Errors surface as toasts
 * and are then consumed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    highlightedFeatureId: String?,
    onBack: () -> Unit,
    viewModel: UpgradeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(highlightedFeatureId) {
        viewModel.setHighlightedFeature(highlightedFeatureId)
    }

    LaunchedEffect(state.error) {
        state.error?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Jupiter Pro") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = if (state.isPro) "You're a Pro!" else "Unlock everything with Pro",
                style = MaterialTheme.typography.headlineSmall,
            )
            state.highlightedFeature?.let { f ->
                if (!state.isPro) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "${f.title} is a Pro feature.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.size(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.features) { feature ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(feature.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                feature.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (state.isPro) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) { Text("Continue") }
            } else {
                Button(
                    onClick = {
                        val activity = context.findActivity()
                        if (activity != null) viewModel.purchase(activity)
                        else Toast.makeText(context, "Cannot start purchase.", Toast.LENGTH_SHORT).show()
                    },
                    enabled = !state.isPurchasing,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    if (state.isPurchasing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Upgrade to Pro")
                    }
                }
                TextButton(
                    onClick = viewModel::restore,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                ) { Text("Restore purchases") }
            }
        }
    }
}

/** Unwraps a [ContextWrapper] chain to the hosting [Activity], or null. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
```

### 6.3 `feature/billing/FeatureGate.kt`

Reusable composable that wraps any premium entry point. While unlocked (free tier fail-open or Pro), it renders `content()` unchanged — **zero behavior change**. While locked, it renders a lock placeholder and a one-tap "Upgrade" that deep-links to the paywall. Gate checks must be **additive wrappers**, never rewrites of existing screen bodies.

```kotlin
package com.jupiter.filemanager.feature.billing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jupiter.filemanager.core.entitlement.Feature

/**
 * Additive gate wrapper for a premium [feature].
 *
 * When the feature is unlocked (which is **always** until billing is configured,
 * thanks to [com.jupiter.filemanager.core.entitlement.EntitlementManager]'s
 * fail-open behavior), [content] renders exactly as before — guaranteeing no
 * regression. When locked, a lock placeholder with an upgrade call-to-action is
 * shown and [content] is never composed.
 */
@Composable
fun FeatureGate(
    feature: Feature,
    onUpgrade: (Feature) -> Unit,
    viewModel: FeatureGateViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val unlocked by viewModel.unlockedFor(feature).collectAsStateWithLifecycle(initialValue = true)
    if (unlocked) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
            Text(
                text = "${feature.title} is a Pro feature",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Text(
                text = feature.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = { onUpgrade(feature) }, modifier = Modifier.padding(top = 20.dp)) {
                Text("Upgrade to Pro")
            }
        }
    }
}
```

`feature/billing/FeatureGateViewModel.kt`:

```kotlin
package com.jupiter.filemanager.feature.billing

import androidx.lifecycle.ViewModel
import com.jupiter.filemanager.core.entitlement.EntitlementManager
import com.jupiter.filemanager.core.entitlement.Feature
import com.jupiter.filemanager.core.entitlement.Tier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class FeatureGateViewModel @Inject constructor(
    private val entitlements: EntitlementManager,
) : ViewModel() {
    /** Cold flow of whether [feature] is currently unlocked. */
    fun unlockedFor(feature: Feature): Flow<Boolean> =
        entitlements.tier.map { it == Tier.PRO || entitlements.isUnlocked(feature) }
}
```

### 6.4 `ui/navigation/Destinations.kt` — add the Paywall destination

Add inside the `sealed class Destination`, after `data object DualPane` (the last entry):

```kotlin
    data object Paywall : Destination("paywall?feature={feature}") {
        const val ARG_FEATURE = "feature"
        fun create(featureId: String): String =
            "paywall?feature=" + android.net.Uri.encode(featureId)
    }
```

### 6.5 `ui/navigation/JupiterNavHost.kt` — wire the Paywall

Add this import alongside the existing feature imports:

```kotlin
import com.jupiter.filemanager.feature.billing.PaywallScreen
```

Add this `composable` block inside the `NavHost { }` body, immediately after the `Destination.DualPane.route` block (the last one):

```kotlin
        composable(
            route = Destination.Paywall.route,
            arguments = listOf(
                navArgument(Destination.Paywall.ARG_FEATURE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val featureId = backStackEntry.arguments?.getString(Destination.Paywall.ARG_FEATURE)
            PaywallScreen(
                highlightedFeatureId = featureId,
                onBack = { navController.popBackStack() },
            )
        }
```

### 6.6 Gate the premium entry points (additive)

Wrap the body of each premium screen in `FeatureGate`. The `onUpgrade` lambda navigates to the paywall. These screens already receive an `onBack`/`onNavigateRoute`-style lambda from `JupiterNavHost`; thread a navigation lambda through where one isn't present. **The wrapping must not alter any existing logic inside `content()`.**

Concretely, for each of the five entry points below, wrap the outermost returned composable. Example for `feature/browser/DualPaneScreen.kt` — locate the top-level `@Composable fun DualPaneScreen(...)` body and wrap its current content:

```kotlin
// at the top of DualPaneScreen's body, the existing content is moved into the lambda:
FeatureGate(
    feature = Feature.DUAL_PANE,
    onUpgrade = { onNavigateRoute(Destination.Paywall.create(it.id)) },
) {
    // ... existing DualPaneScreen content unchanged ...
}
```

Apply the equivalent wrapper with:
- `feature/vault/VaultScreen.kt` → `Feature.VAULT`
- `feature/cloud/CloudHubScreen.kt` and `feature/cloud/NasConnectionsScreen.kt` → `Feature.REMOTE_ACCESS`
- `feature/ai/AiAssistantScreen.kt` → `Feature.AI_SUITE`
- `feature/browser/DualPaneScreen.kt` → `Feature.DUAL_PANE`
- `feature/cleanup/DuplicatesScreen.kt` and `feature/cleanup/SmartMergeScreen.kt` → `Feature.ADVANCED_CLEANUP`

For screens whose signature lacks a route-navigation lambda (e.g. `VaultScreen(onBack)`), add an optional parameter `onUpgrade: (Feature) -> Unit = {}` and pass it from `JupiterNavHost`'s `composable` block, e.g.:

```kotlin
VaultScreen(
    onBack = { navController.popBackStack() },
    onUpgrade = { feature -> navController.navigate(Destination.Paywall.create(feature.id)) },
)
```

Add the imports `com.jupiter.filemanager.core.entitlement.Feature`, `com.jupiter.filemanager.feature.billing.FeatureGate`, and (where needed) `com.jupiter.filemanager.ui.navigation.Destination` to each edited screen. Because the default `onUpgrade = {}` makes the parameter optional, all existing call sites keep compiling.

---

## 7. Phase 4 — Configuration

### 7.1 Keys / env

No secrets are embedded. Billing authenticates via the signed APK + the user's Play account. Keep `BillingConfig.isBillingConfigured = false` until products exist.

### 7.2 External service setup (Play Console)

1. **Create the app** in Play Console (https://play.google.com/console) and upload a signed AAB to an internal-testing track (the release build already uses debug signing per `app/build.gradle.kts`; replace with a real upload key before publishing).
2. **Create products** (https://play.google.com/console → *Monetize → Products*):
   - Subscription `jupiter_pro_monthly` with a `monthly` base plan → activate.
   - (Optional) One-time product `jupiter_pro_lifetime` → activate.
3. **License testers** (https://play.google.com/console → *Setup → License testing*) — add test Google accounts so purchases are free/refunded automatically.
4. **Flip the switch:** set `BillingConfig.isBillingConfigured = true` and verify `PRODUCT_ID_*` match the console exactly.
5. (Future, out of scope) Server-side purchase verification via Play Developer API + RTDN — the code is structured so a server check can replace the local `isPro` derivation without touching the UI.

### 7.3 ProGuard / R8 — `app/proguard-rules.pro`

Append (release uses `isMinifyEnabled = true`):

```proguard
# Google Play Billing (v7) — keep API surface used via reflection by the library
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.api.**

# Entitlement enums referenced by name (Feature.id mapping)
-keepclassmembers enum com.jupiter.filemanager.core.entitlement.** { *; }
```

(`domain.model.**` is already kept; `Feature`/`Tier` live under `core.entitlement`, hence the extra rule.)

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

**A. Default fail-open build (`isBillingConfigured = false`) — regression guard**
1. `./gradlew :app:assembleDebug` → builds.
2. Install, launch. Navigate to Vault, Cloud Hub, NAS Connections, AI Assistant, Dual-Pane, Duplicates, Smart Merge.
3. **Expected:** every screen behaves exactly as before (no lock screen, no paywall). This proves zero regression.
4. Open the paywall route manually (e.g. via a temporary nav action) → it shows "You're a Pro!" (because tier is PRO).

**B. Billing-enabled build (`isBillingConfigured = true`, real products, license tester)**
1. Fresh install (no prior purchase). Open Vault → **locked** placeholder + "Upgrade to Pro".
2. Tap Upgrade → Paywall lists all five features, Vault highlighted.
3. Tap "Upgrade to Pro" → Play purchase sheet appears → complete as license tester.
4. **Expected:** within seconds `isPro` flips; back-navigate to Vault → now unlocked.
5. Kill & relaunch → Vault still unlocked (cache + Play re-query).
6. "Restore purchases" on a second device with the same account → unlocks.
7. Cancel the purchase sheet → toast/no crash; feature stays locked.

### 8.2 Recommended automated tests

**Unit (`app/src/test/...`, JUnit + coroutines-test — both already in the catalog):**
- `EntitlementManagerTest`: with `isBillingConfigured = false`, `tier` is always `PRO` and `isUnlocked(any)` is `true`. (Inject a fake `BillingClientWrapper` exposing a controllable `isPro` `MutableStateFlow` and a fake cache.)
- `UpgradeViewModelTest`: `purchase()` with a failing fake wrapper sets `error` and clears `isPurchasing`; `consumeError()` clears it.
- `EntitlementCacheDataStoreTest`: write `true` → `isProCached` emits `true`.

**Instrumented (`app/src/androidTest/...`, Compose UI test):**
- `FeatureGateTest`: gate with a fake VM emitting `unlocked = false` shows "Upgrade to Pro"; emitting `true` renders the child content.

---

## 9. Error handling & edge cases (≥6)

1. **Billing service unavailable / no Play Store** — `ensureConnected()` returns `false`; `refreshPurchases()` sets `isPro = false`; `launchPurchase()` returns `AppResult.Failure(AppError.BillingUnavailable)` → paywall toasts the message, no crash.
2. **User cancels the purchase sheet** — `PurchasesUpdatedListener` ignores `USER_CANCELED`; `launchBillingFlow` returns OK so no false error; the feature simply stays locked. No exception propagates.
3. **Product not yet active in Play Console** — `queryProProductDetails()` returns `null` → `launchPurchase()` returns `AppResult.Failure(AppError.BillingUnavailable("Pro product is not available right now."))`.
4. **Pending / deferred purchase** — `handlePurchases` only flips `isPro` on `PurchaseState.PURCHASED`; pending purchases do not unlock and are not acknowledged until they complete (Play re-delivers on next query).
5. **Unacknowledged purchase (Play auto-refund risk)** — `handlePurchases` acknowledges any purchased-but-unacknowledged entitlement idempotently; acknowledgement failure is non-fatal (retried on next refresh).
6. **DataStore read failure on cold start** — `EntitlementCacheDataStore.isProCached` catches `IOException` and emits `false`; the live Play query is authoritative and corrects it. No crash.
7. **`isBillingConfigured = false` (CI / demo / pre-monetization users)** — `EntitlementManager.tier` is hard-wired to `PRO`; `FeatureGate` always renders `content()`. Guarantees no regression regardless of Play availability.
8. **No hosting Activity for purchase launch** — `PaywallScreen.findActivity()` returns `null` → a toast is shown instead of crashing; `launchBillingFlow` is never called with a bad context.

---

## 10. Integration with other initiatives

- **Foundation for all monetized initiatives.** `EntitlementManager` + `Feature` are the shared gate every later paid surface reuses. New paid features add a `Feature` enum value and a `FeatureGate` wrapper — no new billing plumbing.
- **#2 Cloud quotas / #3 AI credits:** consume `EntitlementManager.tier` to switch free vs. Pro limits; they extend `Feature` (`REMOTE_ACCESS`, `AI_SUITE` already present) rather than reimplement entitlement.
- **#5 Analytics/telemetry initiative:** can subscribe to `EntitlementManager.tier` and `Feature.id` for conversion funnels (paywall impressions → purchase).
- **#7 Team/enterprise licensing:** replaces the local `isPro` derivation in `EntitlementManager` with a server-verified source while keeping the same interface and UI.
- **No hard ordering dependency:** this initiative is self-contained and additive; it can ship first and unblock the rest. It depends only on already-shipped premium screens existing (they do).

---

## 11. Rollback plan

The change is purely additive; rollback is removal.

1. **Feature-flag rollback (instant, no code removal):** set `BillingConfig.isBillingConfigured = false`. Every gate becomes fail-open → app behaves exactly as pre-initiative. This is the recommended first response to any production issue.
2. **Full revert:** delete the new packages `core/entitlement/` and `feature/billing/`, the `di/EntitlementModule.kt`, the two new `AppError` variants, the `Destination.Paywall` block, the Paywall `composable` in `JupiterNavHost.kt`, and the `FeatureGate` wrappers + `onUpgrade` params from the six gated screens. Remove `billing-ktx` from `libs.versions.toml` and `app/build.gradle.kts`, and the ProGuard rules. Because every gated screen kept its original body inside `content()` and `onUpgrade` defaulted to `{}`, unwrapping is mechanical and call sites are unaffected.
3. **Git:** the work should be a single feature branch/PR; `git revert` of that PR fully restores the prior state.

---

## 12. Definition of done

- [ ] `gradle/libs.versions.toml` and `app/build.gradle.kts` add `com.android.billingclient:billing-ktx:7.1.1`; project syncs.
- [ ] `core/entitlement/` contains `Feature`, `Tier`, `EntitlementManager` (interface) + `DefaultEntitlementManager` (impl), all compiling.
- [ ] `feature/billing/` contains `BillingConfig`, `BillingClientWrapper`, `EntitlementCacheDataStore`, `UpgradeViewModel`, `UpgradeUiState`, `PaywallScreen`, `FeatureGate`, `FeatureGateViewModel`.
- [ ] `di/EntitlementModule.kt` provides `@ApplicationScope CoroutineScope` and binds `EntitlementManager`; Hilt graph compiles (KSP succeeds).
- [ ] Two new `AppError` variants added without altering existing variants; cross-boundary failures use `AppResult`/`AppError` and IO uses `@IoDispatcher`.
- [ ] `Destination.Paywall` added and wired in `JupiterNavHost.kt`; deep-link with a `feature` arg highlights that feature.
- [ ] All six premium entry points wrapped in `FeatureGate` **additively**; existing screen bodies unchanged; `onUpgrade` defaults keep all existing call sites compiling.
- [ ] **Fail-open verified:** with `isBillingConfigured = false`, every gated screen renders normally and the paywall shows the Pro state.
- [ ] **No regression: existing Vault unlock/import/export still works** when unlocked (free fail-open mode).
- [ ] **No regression: existing NAS/SMB/SFTP/FTP remote browsing and Cloud Hub still work**; AI Assistant, Dual-Pane, Duplicates and Smart Merge all open and function as before.
- [ ] ProGuard rules added; `./gradlew :app:assembleRelease` (minified) produces an installable APK with billing classes kept.
- [ ] **CI green: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both pass.**
- [ ] Manual smoke-test script (Section 8.1, scenario A at minimum) executed and passing.
