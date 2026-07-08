# Initiative #6 — Privacy-first Opt-in Product Analytics & Crash Reporting

> Ultra-detailed Android implementation prompt for an AI coding agent.
> Target codebase: **Jupiter** file manager — `/home/user/Jupiter`
> Stack: Kotlin 2.0.21 · Jetpack Compose · Material3 · Hilt 2.52 · Coroutines 1.9 · DataStore · OkHttp · KSP
> Package root: `com.jupiter.filemanager` at `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`

---

## 1. Initiative header

**Title:** Privacy-first Opt-in Product Analytics & Crash Reporting
**Value range:** **+€80k – €170k**

**Business case.** Jupiter currently ships with a strong "privacy-first" promise but **no way to measure product behaviour** — there is no activation funnel, no retention signal, no conversion instrumentation, and no crash visibility. That blindness is a direct revenue and valuation problem: the team cannot tell which features drive upgrades (see Initiative #1 Pro monetization), cannot diagnose the crashes that quietly suppress Play Store rating, and cannot present an acquirer with the engagement metrics that justify a higher multiple. This initiative adds a **vendor-neutral, opt-in (default OFF), no-PII analytics + crash abstraction**: a tiny `Analytics` interface with an inert `NoOpAnalytics` default, a single user-controlled privacy toggle, and a pluggable sink so a real backend (or none) can be wired later without touching call sites. Because it is **default-OFF and emits nothing until the user explicitly consents**, it preserves the privacy-first brand verbatim while de-risking the product for both growth experiments and due diligence. The work is **purely additive** — every new event call is a one-line, fail-safe invocation that cannot regress an existing feature.

---

## 2. Codebase context

### 2.1 Relevant real files that EXIST today (read these first)

```
app/src/main/java/com/jupiter/filemanager/
├── JupiterApp.kt                                  # @HiltAndroidApp Application + WorkManager config
├── MainActivity.kt                                # single-activity host, JupiterNavHost
├── core/
│   └── result/
│       ├── AppResult.kt                           # sealed AppResult<Success|Failure> + map/onSuccess/onFailure
│       └── AppError.kt                            # sealed AppError (PermissionDenied/NotFound/Io/Unknown…)
├── di/
│   ├── CoroutineModule.kt                         # @IoDispatcher/@DefaultDispatcher/@MainDispatcher qualifiers
│   ├── AiModule.kt                                # @Binds AiAssistant -> AnthropicAiAssistant  (pattern to mirror)
│   ├── RepositoryModule.kt                        # @Binds core repos
│   ├── FeatureRepositoryModule.kt                 # @Binds feature repos (additive module pattern)
│   └── RemoteModule.kt                            # @Binds remote repos
├── data/
│   └── preferences/
│       ├── SettingsDataStore.kt                   # Preferences DataStore "jupiter_settings" (EXTEND here)
│       └── AppStateDataStore.kt                   # Preferences DataStore "jupiter_app_state"
├── feature/
│   ├── ai/
│   │   └── AnthropicAiAssistant.kt                # @Singleton OkHttp client on @IoDispatcher (network pattern)
│   ├── settings/
│   │   ├── SettingsScreen.kt                      # Compose settings UI (ADD privacy toggle here)
│   │   └── SettingsViewModel.kt                   # @HiltViewModel reading SettingsDataStore (EXTEND)
│   ├── privacy/
│   │   ├── PrivacyDashboardScreen.kt              # existing privacy UI (link to analytics consent)
│   │   ├── PrivacyDashboardUiState.kt
│   │   └── PrivacyDashboardViewModel.kt
│   ├── analytics/                                 # ⚠️ EXISTING = STORAGE analytics (DO NOT clash)
│   │   ├── StorageAnalyticsScreen.kt
│   │   ├── StorageAnalyticsUiState.kt
│   │   └── StorageAnalyticsViewModel.kt
│   ├── onboarding/OnboardingScreen.kt             # funnel point: onboarding completed
│   ├── vault/VaultScreen.kt                       # funnel point: vault unlocked
│   └── main/MainScreen.kt                         # funnel point: app shell shown
└── ui/
    └── navigation/
        ├── Destinations.kt                        # sealed Destination(route)  (ADD AnalyticsConsent)
        └── JupiterNavHost.kt                      # NavHost wiring (ADD composable)
```

Build files:
```
gradle/libs.versions.toml                          # version catalog (OkHttp already present)
app/build.gradle.kts                               # app module (OkHttp already a dependency)
app/src/main/AndroidManifest.xml                   # has INTERNET? -> verify in Phase 1
```

### 2.2 What exists vs missing vs needs change

| Concern | Status | Action |
|---|---|---|
| `AppResult` / `AppError` | **Exists** | Reuse as-is for the sink. |
| `@IoDispatcher` qualifier | **Exists** | Reuse for off-main dispatch. |
| `SettingsDataStore` | **Exists** | **Change (additive):** add `analyticsEnabled` + `crashReportingEnabled` keys/flows/setters. |
| `SettingsViewModel` / `SettingsScreen` | **Exists** | **Change (additive):** surface the new toggles. |
| OkHttp dependency | **Exists** (`libs.okhttp`) | Reuse; no new Gradle coordinate strictly required. |
| `INTERNET` permission | **Verify** | Add to manifest if missing (network sink needs it). |
| `core/analytics/*` (interface, event model, NoOp, gate) | **MISSING** | **Create** — the heart of this initiative. |
| `di/AnalyticsModule.kt` | **MISSING** | **Create** — binds `Analytics` -> gated impl. |
| `feature/consent/*` (consent screen + VM) | **MISSING** | **Create** — first-run consent prompt. |
| Funnel event calls | **MISSING** | **Add** one-liners at onboarding/main/vault/settings/conversion points. |
| `Destination.AnalyticsConsent` + nav wiring | **MISSING** | **Create**. |
| ProGuard keep rules for event enum | **MISSING** | **Add** to `app/proguard-rules.pro`. |

> **Naming guard:** the existing `feature/analytics` package is **storage** analytics (disk usage). All NEW product-analytics code lives in **`core/analytics`** and **`feature/consent`** to avoid any symbol or package collision. Never edit `StorageAnalytics*` files.

---

## 3. Pre-conditions

### 3.1 Gradle dependencies

OkHttp (`com.squareup.okhttp3:okhttp:4.12.0`) is **already** declared (`libs.okhttp`, used by `AnthropicAiAssistant` and `WebDavFileSource`). The default `NoOpAnalytics` and the network sink reuse it — **no new third-party coordinate is mandatory**. `org.json` is bundled with the Android platform (already used by `AnthropicAiAssistant`).

Optional (only if you also want a JVM unit test for the network sink with a fake server) — add to the version catalog:

```toml
# gradle/libs.versions.toml  -> [versions]
mockwebserver = "4.12.0"          # matches okhttp = "4.12.0"

# gradle/libs.versions.toml  -> [libraries]
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "mockwebserver" }
```
```kotlin
// app/build.gradle.kts -> dependencies { ... }
testImplementation(libs.okhttp.mockwebserver)
```

### 3.2 Manifest / permission / external-service prerequisites

- **Permission:** `android.permission.INTERNET` is required for the network sink. The default build uses `NoOpAnalytics` and emits nothing, but the permission must exist for the pluggable network sink to function. (Add in Phase 1 if absent.)
- **No Play Console prerequisite.** This abstraction is **vendor-neutral**: it ships with `NoOpAnalytics` and an HTTP sink that posts to a configurable endpoint. No Firebase/GA/Crashlytics SDK is added, so **no `google-services.json`, no Play Console linkage, no Firebase project** is needed to build, ship, or pass review.
- **API key / endpoint (optional, runtime-configured):** the network sink reads an endpoint URL + write key from `SettingsDataStore` (added Phase 2). If both are blank the sink degrades to NoOp. There is **no compile-time secret**; nothing is hard-coded.
- **Data-safety:** because emission is default-OFF and gated by explicit consent, the Play Data Safety form remains "no data collected" until the user opts in; document the opt-in path in `docs/` (out of scope for code but noted in DoD).

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `app/src/main/AndroidManifest.xml`

Ensure the `INTERNET` permission is declared. Add this line directly **after** the `POST_NOTIFICATIONS` permission block (do not remove or reorder existing permissions):

```xml
    <!-- Required for the opt-in product-analytics / crash network sink.
         Default build uses NoOpAnalytics and emits nothing; this permission only
         enables the optional, user-consented network sink. -->
    <uses-permission android:name="android.permission.INTERNET" />
```

### 4.2 `gradle/libs.versions.toml`

No change required for the production feature (OkHttp already present). Apply the optional `mockwebserver` entries from §3.1 **only** if you implement the network-sink unit test.

### 4.3 `app/build.gradle.kts`

No production dependency change required. The feature uses already-present `libs.okhttp`, `libs.hilt.android`, `libs.androidx.datastore.preferences`, `libs.kotlinx.coroutines.android`, `libs.androidx.navigation.compose`.

### 4.4 String resources

Add to `app/src/main/res/values/strings.xml` (create entries; do not remove existing):

```xml
    <!-- Opt-in product analytics & crash reporting -->
    <string name="analytics_consent_title">Help improve Jupiter</string>
    <string name="analytics_consent_body">Jupiter is private by default. You can optionally share anonymous, no-personal-data usage and crash diagnostics to help us fix bugs and improve features. This is off until you turn it on, never includes file names, paths, or content, and you can change it any time in Settings.</string>
    <string name="analytics_consent_enable">Share anonymous diagnostics</string>
    <string name="analytics_consent_decline">Keep everything private</string>
    <string name="settings_analytics_title">Anonymous usage analytics</string>
    <string name="settings_analytics_subtitle">Opt-in, no personal data. Off by default.</string>
    <string name="settings_crash_title">Crash diagnostics</string>
    <string name="settings_crash_subtitle">Send anonymous crash reports to help fix bugs.</string>
```

---

## 5. Phase 2 — Data / domain layer

All files below are **new** unless marked **(EDIT — additive)**. Create the package directory `core/analytics`.

### 5.1 `core/analytics/AnalyticsEvent.kt` (NEW)

```kotlin
package com.jupiter.filemanager.core.analytics

/**
 * Closed catalogue of product-analytics events Jupiter is allowed to emit.
 *
 * Modelled as an enum so the set of events is finite, reviewable, and impossible
 * to populate with free-form (potentially PII-bearing) strings at a call site.
 * Each constant carries a stable wire [key] used by sinks; the key is decoupled
 * from the Kotlin name so the enum can be refactored without breaking a backend.
 *
 * NOTHING in this catalogue references a file name, path, MIME content, account
 * identifier, or any user-entered text. Events are coarse funnel signals only.
 */
enum class AnalyticsEvent(val key: String) {
    /** App shell became visible (session start proxy). */
    APP_OPENED("app_opened"),

    /** User finished the onboarding flow. */
    ONBOARDING_COMPLETED("onboarding_completed"),

    /** Storage permission granted (activation gate passed). */
    PERMISSION_GRANTED("permission_granted"),

    /** Encrypted vault unlocked successfully (engagement signal). */
    VAULT_UNLOCKED("vault_unlocked"),

    /** A premium/Pro upgrade surface was shown (top of conversion funnel). */
    PAYWALL_VIEWED("paywall_viewed"),

    /** A Pro feature was successfully activated/purchased (conversion). */
    PRO_UPGRADED("pro_upgraded"),

    /** A cloud/remote connection was added (retention-driving feature). */
    REMOTE_CONNECTION_ADDED("remote_connection_added"),

    /** A file-transfer session started (engagement). */
    TRANSFER_STARTED("transfer_started"),

    /** The AI assistant was invoked (premium feature usage). */
    AI_USED("ai_used"),

    /** User opted IN to analytics from the consent surface. */
    ANALYTICS_OPT_IN("analytics_opt_in"),

    /** User opted OUT of analytics. */
    ANALYTICS_OPT_OUT("analytics_opt_out"),
}

/**
 * Allow-listed, non-PII property keys an event may carry.
 *
 * Values supplied at call sites MUST be coarse, bounded, non-identifying tokens
 * (e.g. "smb", "sftp", "free", "pro"). Never pass paths, names, or user text.
 */
enum class AnalyticsProperty(val key: String) {
    /** Coarse source/protocol bucket, e.g. "smb"/"ftp"/"sftp"/"webdav". */
    SOURCE("source"),

    /** Coarse surface identifier the event originated from, e.g. "settings". */
    SURFACE("surface"),

    /** Coarse tier, e.g. "free"/"pro". */
    TIER("tier"),
}
```

### 5.2 `core/analytics/Analytics.kt` (NEW)

```kotlin
package com.jupiter.filemanager.core.analytics

/**
 * Vendor-neutral product-analytics + crash abstraction.
 *
 * The entire app depends ONLY on this interface; the concrete sink (NoOp,
 * in-memory, or network) is chosen by Hilt. Implementations MUST be fail-safe:
 * a sink failure must never propagate to a feature call site. All methods are
 * therefore non-suspending, non-throwing, and return Unit — callers fire and
 * forget. Sinks that do real IO must dispatch it off the caller's thread.
 *
 * Emission is gated by user consent. Implementations consult the consent gate
 * and become a silent NoOp whenever analytics is disabled (the default).
 */
interface Analytics {

    /** True when the user has opted in AND a sink is active. Cheap, non-throwing. */
    val isEnabled: Boolean

    /**
     * Records a funnel [event] with optional non-PII [properties].
     *
     * Keys are constrained by [AnalyticsProperty]; values must be coarse,
     * bounded tokens. No-op when analytics is disabled.
     */
    fun track(event: AnalyticsEvent, properties: Map<AnalyticsProperty, String> = emptyMap())

    /**
     * Records a non-fatal/handled error for diagnostics. The [domain] is a
     * coarse subsystem tag (e.g. "transfer", "remote"); [message] must be a
     * developer string with NO user data. No-op when crash reporting is disabled.
     */
    fun recordError(domain: String, message: String, throwable: Throwable? = null)
}
```

### 5.3 `core/analytics/AnalyticsSink.kt` (NEW — pluggable backend SPI)

```kotlin
package com.jupiter.filemanager.core.analytics

import com.jupiter.filemanager.core.result.AppResult

/**
 * Pluggable transport for analytics payloads.
 *
 * A sink is the only place that performs IO. It is decoupled from consent and
 * threading concerns (handled by the gated [Analytics] implementation) so a sink
 * can be swapped — NoOp, in-memory (for tests/QA), or HTTP — without changing any
 * feature code. Sinks return [AppResult] rather than throwing.
 */
interface AnalyticsSink {

    /** Delivers a single serialized [payload]. */
    suspend fun send(payload: AnalyticsPayload): AppResult<Unit>
}

/**
 * Wire-ready representation of one event. Built exclusively from the closed
 * [AnalyticsEvent]/[AnalyticsProperty] catalogues plus a coarse timestamp; it can
 * structurally never carry PII.
 */
data class AnalyticsPayload(
    val eventKey: String,
    val properties: Map<String, String>,
    val epochMillis: Long,
    val isError: Boolean = false,
    val errorDomain: String? = null,
    val errorMessage: String? = null,
)
```

### 5.4 `core/analytics/NoOpAnalyticsSink.kt` (NEW — the default transport)

```kotlin
package com.jupiter.filemanager.core.analytics

import com.jupiter.filemanager.core.result.AppResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AnalyticsSink] that discards every payload and always succeeds.
 *
 * Shipped as the bound sink so a stock build NEVER sends a network request,
 * preserving Jupiter's privacy-first promise even if a user opts in before a real
 * endpoint is configured.
 */
@Singleton
class NoOpAnalyticsSink @Inject constructor() : AnalyticsSink {
    override suspend fun send(payload: AnalyticsPayload): AppResult<Unit> = AppResult.Success(Unit)
}
```

### 5.5 `core/analytics/HttpAnalyticsSink.kt` (NEW — optional network transport, vendor-neutral)

```kotlin
package com.jupiter.filemanager.core.analytics

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vendor-neutral HTTP [AnalyticsSink] that POSTs a compact JSON payload to a
 * user/operator-configured endpoint. Mirrors the OkHttp usage in
 * [com.jupiter.filemanager.feature.ai.AnthropicAiAssistant]: a single shared
 * client with finite timeouts, all IO on [IoDispatcher], failures returned as
 * [AppResult.Failure] (never thrown).
 *
 * When the endpoint URL is blank the sink degrades to a successful no-op so an
 * opted-in user with no backend configured behaves exactly like [NoOpAnalyticsSink].
 */
@Singleton
class HttpAnalyticsSink @Inject constructor(
    private val settings: SettingsDataStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : AnalyticsSink {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun send(payload: AnalyticsPayload): AppResult<Unit> = withContext(io) {
        val endpoint = settings.analyticsEndpoint.first().trim()
        if (endpoint.isBlank()) return@withContext AppResult.Success(Unit) // no backend -> silent no-op
        val writeKey = settings.analyticsWriteKey.first().trim()

        val json = JSONObject().apply {
            put("event", payload.eventKey)
            put("ts", payload.epochMillis)
            put("error", payload.isError)
            payload.errorDomain?.let { put("error_domain", it) }
            payload.errorMessage?.let { put("error_message", it) }
            val props = JSONObject()
            payload.properties.forEach { (k, v) -> props.put(k, v) }
            put("properties", props)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(endpoint)
            .apply { if (writeKey.isNotBlank()) header("Authorization", "Bearer $writeKey") }
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    AppResult.Success(Unit)
                } else {
                    AppResult.Failure(AppError.Io("Analytics endpoint returned HTTP ${response.code}"))
                }
            }
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io("Analytics network failure", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown("Analytics send failed", e))
        }
    }
}
```

### 5.6 `core/analytics/GatedAnalytics.kt` (NEW — consent gate + dispatch, the bound `Analytics`)

```kotlin
package com.jupiter.filemanager.core.analytics

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The single [Analytics] implementation bound app-wide.
 *
 * Responsibilities:
 *  - **Consent gate:** reads [SettingsDataStore.analyticsEnabled] /
 *    [SettingsDataStore.crashReportingEnabled]; emits NOTHING unless the relevant
 *    flag is true (both default false -> default-OFF, privacy preserved).
 *  - **Threading:** fire-and-forget dispatch onto a private [IoDispatcher]-backed
 *    scope so call sites never block and never see an exception.
 *  - **Isolation:** delegates transport to the injected [AnalyticsSink], whose
 *    failures are swallowed (analytics must never break a feature).
 *
 * The [isEnabled] read uses a fast, fail-safe runBlocking on the DataStore (same
 * approach as [com.jupiter.filemanager.feature.ai.AnthropicAiAssistant.isEnabled]).
 */
@Singleton
class GatedAnalytics @Inject constructor(
    private val settings: SettingsDataStore,
    private val sink: AnalyticsSink,
    @IoDispatcher private val io: CoroutineDispatcher,
) : Analytics {

    private val scope = CoroutineScope(SupervisorJob() + io)

    override val isEnabled: Boolean
        get() = runCatching { runBlocking { settings.analyticsEnabled.first() } }.getOrDefault(false)

    override fun track(event: AnalyticsEvent, properties: Map<AnalyticsProperty, String>) {
        scope.launch {
            runCatching {
                if (!settings.analyticsEnabled.first()) return@launch // consent gate
                val payload = AnalyticsPayload(
                    eventKey = event.key,
                    properties = properties.entries.associate { it.key.key to it.value },
                    epochMillis = System.currentTimeMillis(),
                )
                val result = sink.send(payload)
                if (result is AppResult.Failure) {
                    // Swallow: analytics delivery failure must never affect the app.
                }
            }
        }
    }

    override fun recordError(domain: String, message: String, throwable: Throwable?) {
        scope.launch {
            runCatching {
                if (!settings.crashReportingEnabled.first()) return@launch // separate consent gate
                val payload = AnalyticsPayload(
                    eventKey = "error",
                    properties = emptyMap(),
                    epochMillis = System.currentTimeMillis(),
                    isError = true,
                    errorDomain = domain,
                    errorMessage = buildString {
                        append(message)
                        throwable?.let { append(" | ").append(it.javaClass.simpleName) }
                    },
                )
                sink.send(payload)
            }
        }
    }
}
```

### 5.7 `core/analytics/NoOpAnalytics.kt` (NEW — inert fallback, for previews/tests/manual disable)

```kotlin
package com.jupiter.filemanager.core.analytics

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Completely inert [Analytics] used by Compose previews, unit tests, and as a
 * drop-in if the team chooses to disable product analytics at the DI layer
 * without removing call sites. Records nothing and reports itself disabled.
 *
 * Kept available (like [com.jupiter.filemanager.feature.ai.NoOpAiAssistant]) but
 * not bound by default; [GatedAnalytics] is the production binding.
 */
@Singleton
class NoOpAnalytics @Inject constructor() : Analytics {
    override val isEnabled: Boolean = false
    override fun track(event: AnalyticsEvent, properties: Map<AnalyticsProperty, String>) = Unit
    override fun recordError(domain: String, message: String, throwable: Throwable?) = Unit
}
```

### 5.8 `data/preferences/SettingsDataStore.kt` (EDIT — additive)

Add two consent keys plus optional network-sink config. **Do not touch existing keys/flows/setters.**

Inside `private object Keys { ... }` add:
```kotlin
        val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")
        val CRASH_REPORTING_ENABLED = booleanPreferencesKey("crash_reporting_enabled")
        val ANALYTICS_CONSENT_ASKED = booleanPreferencesKey("analytics_consent_asked")
        val ANALYTICS_ENDPOINT = stringPreferencesKey("analytics_endpoint")
        val ANALYTICS_WRITE_KEY = stringPreferencesKey("analytics_write_key")
```

After the existing `aiApiKey` flow, add the new flows:
```kotlin
    /** Whether opt-in product analytics is enabled; defaults to false (privacy-first). */
    val analyticsEnabled: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ANALYTICS_ENABLED] ?: false }

    /** Whether crash/error diagnostics are enabled; defaults to false. */
    val crashReportingEnabled: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.CRASH_REPORTING_ENABLED] ?: false }

    /** Whether the first-run analytics consent prompt has been shown; defaults to false. */
    val analyticsConsentAsked: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ANALYTICS_CONSENT_ASKED] ?: false }

    /** Operator-configured analytics endpoint URL; blank -> network sink is a no-op. */
    val analyticsEndpoint: Flow<String> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ANALYTICS_ENDPOINT] ?: "" }

    /** Optional bearer write key for the analytics endpoint; defaults to "". */
    val analyticsWriteKey: Flow<String> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ANALYTICS_WRITE_KEY] ?: "" }
```

After the existing `setAiApiKey` setter, add:
```kotlin
    suspend fun setAnalyticsEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ANALYTICS_ENABLED] = value }
    }

    suspend fun setCrashReportingEnabled(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.CRASH_REPORTING_ENABLED] = value }
    }

    suspend fun setAnalyticsConsentAsked(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ANALYTICS_CONSENT_ASKED] = value }
    }

    suspend fun setAnalyticsEndpoint(value: String) {
        dataStore.edit { prefs -> prefs[Keys.ANALYTICS_ENDPOINT] = value }
    }

    suspend fun setAnalyticsWriteKey(value: String) {
        dataStore.edit { prefs -> prefs[Keys.ANALYTICS_WRITE_KEY] = value }
    }
```

### 5.9 `di/AnalyticsModule.kt` (NEW — mirrors `AiModule`/`RepositoryModule`)

```kotlin
package com.jupiter.filemanager.di

import com.jupiter.filemanager.core.analytics.Analytics
import com.jupiter.filemanager.core.analytics.AnalyticsSink
import com.jupiter.filemanager.core.analytics.GatedAnalytics
import com.jupiter.filemanager.core.analytics.NoOpAnalyticsSink
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the product-analytics abstraction.
 *
 *  - [Analytics]     -> [GatedAnalytics]      (consent-gated, fail-safe dispatcher)
 *  - [AnalyticsSink] -> [NoOpAnalyticsSink]   (DEFAULT transport: emits NOTHING)
 *
 * To enable a real backend later, change ONLY the [AnalyticsSink] binding to
 * `HttpAnalyticsSink` — no feature call site changes. Each impl is a
 * `@Singleton` with an `@Inject` constructor; this module only declares bindings.
 * Separate module from [AiModule]/[RepositoryModule]; it modifies neither.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    abstract fun bindAnalytics(impl: GatedAnalytics): Analytics

    @Binds
    abstract fun bindAnalyticsSink(impl: NoOpAnalyticsSink): AnalyticsSink
}
```

> To flip on the network sink, change `NoOpAnalyticsSink` to `HttpAnalyticsSink` in the `@Binds` above. Nothing else changes.

---

## 6. Phase 3 — Presentation

### 6.1 `feature/consent/AnalyticsConsentViewModel.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.core.analytics.Analytics
import com.jupiter.filemanager.core.analytics.AnalyticsEvent
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the first-run analytics consent surface. Persists the user's choice via
 * [SettingsDataStore] and records the opt-in/opt-out decision through [Analytics]
 * (the opt-in event only actually transmits once consent has been written and the
 * gate is open — opt-out emits nothing).
 */
@HiltViewModel
class AnalyticsConsentViewModel @Inject constructor(
    private val settings: SettingsDataStore,
    private val analytics: Analytics,
) : ViewModel() {

    /** User accepted: enable analytics + crash reporting, mark prompt as shown. */
    fun accept(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setAnalyticsEnabled(true)
            settings.setCrashReportingEnabled(true)
            settings.setAnalyticsConsentAsked(true)
            analytics.track(AnalyticsEvent.ANALYTICS_OPT_IN)
            onDone()
        }
    }

    /** User declined: keep everything off, mark prompt as shown so it never reappears. */
    fun decline(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setAnalyticsEnabled(false)
            settings.setCrashReportingEnabled(false)
            settings.setAnalyticsConsentAsked(true)
            onDone()
        }
    }
}
```

### 6.2 `feature/consent/AnalyticsConsentScreen.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.consent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.jupiter.filemanager.R

/**
 * First-run, privacy-respecting consent surface for opt-in analytics.
 *
 * Presents a clear value exchange with two equally weighted actions and NO
 * pre-checked state. Declining is a first-class choice. Persistence is delegated
 * entirely to [AnalyticsConsentViewModel]; this composable does no IO.
 *
 * @param onFinished invoked after the user makes a choice, to advance routing.
 */
@Composable
fun AnalyticsConsentScreen(
    onFinished: () -> Unit,
) {
    val viewModel: AnalyticsConsentViewModel = hiltViewModel()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PrivacyTip,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.analytics_consent_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.analytics_consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { viewModel.accept(onFinished) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.analytics_consent_enable))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { viewModel.decline(onFinished) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.analytics_consent_decline))
            }
        }
    }
}
```

### 6.3 `feature/settings/SettingsViewModel.kt` (EDIT — additive)

Surface the two toggles. Update `SettingsUiState`, inject nothing new (already has `SettingsDataStore`), but **note:** `combine` supports at most 5 flows positionally; the file already combines 5. Replace the body with a nested-combine that adds the two analytics flags. Full replacement of the class body:

```kotlin
data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val showHidden: Boolean = false,
    val dualPaneEnabled: Boolean = false,
    val aiEnabled: Boolean = false,
    val aiApiKey: String = "",
    val analyticsEnabled: Boolean = false,
    val crashReportingEnabled: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsDataStore,
) : ViewModel() {

    private val core = combine(
        settings.themeMode,
        settings.showHidden,
        settings.dualPaneEnabled,
        settings.aiEnabled,
        settings.aiApiKey,
    ) { themeMode, showHidden, dualPaneEnabled, aiEnabled, aiApiKey ->
        SettingsUiState(
            themeMode = themeMode,
            showHidden = showHidden,
            dualPaneEnabled = dualPaneEnabled,
            aiEnabled = aiEnabled,
            aiApiKey = aiApiKey,
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        core,
        settings.analyticsEnabled,
        settings.crashReportingEnabled,
    ) { base, analyticsEnabled, crashReportingEnabled ->
        base.copy(
            analyticsEnabled = analyticsEnabled,
            crashReportingEnabled = crashReportingEnabled,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState(),
    )

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }
    fun setShowHidden(value: Boolean) { viewModelScope.launch { settings.setShowHidden(value) } }
    fun setDualPane(value: Boolean) { viewModelScope.launch { settings.setDualPaneEnabled(value) } }
    fun setAiEnabled(value: Boolean) { viewModelScope.launch { settings.setAiEnabled(value) } }
    fun setAiApiKey(value: String) { viewModelScope.launch { settings.setAiApiKey(value.trim()) } }

    fun setAnalyticsEnabled(value: Boolean) {
        viewModelScope.launch { settings.setAnalyticsEnabled(value) }
    }

    fun setCrashReporting(value: Boolean) {
        viewModelScope.launch { settings.setCrashReportingEnabled(value) }
    }
}
```

### 6.4 `feature/settings/SettingsScreen.kt` (EDIT — additive)

The screen already renders `Switch`-style preference rows (it uses `Switch`, `Icon`, `HorizontalDivider`). Add two rows in the existing settings `Column`, after the AI section and before the About section. Use the screen's existing row composable if present; otherwise inline two rows following the established pattern:

```kotlin
            HorizontalDivider()

            // ---- Privacy: opt-in product analytics (default OFF) ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Insights,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_analytics_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_analytics_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.analyticsEnabled,
                    onCheckedChange = viewModel::setAnalyticsEnabled,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_crash_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_crash_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = uiState.crashReportingEnabled,
                    onCheckedChange = viewModel::setCrashReporting,
                )
            }
```

Add the required imports to `SettingsScreen.kt` (only if not already present):
```kotlin
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Insights
import androidx.compose.ui.res.stringResource
import com.jupiter.filemanager.R
```

### 6.5 `ui/navigation/Destinations.kt` (EDIT — additive)

Add under the "Privacy & automation" section:
```kotlin
    data object AnalyticsConsent : Destination("analytics_consent")
```

### 6.6 `ui/navigation/JupiterNavHost.kt` (EDIT — additive)

Add the import alongside the other feature imports:
```kotlin
import com.jupiter.filemanager.feature.consent.AnalyticsConsentScreen
```

Add the composable inside the `NavHost { ... }` block (e.g. next to the `Settings` composable):
```kotlin
        composable(route = Destination.AnalyticsConsent.route) {
            AnalyticsConsentScreen(
                onFinished = {
                    navController.navigate(Destination.Main.route) {
                        popUpTo(Destination.AnalyticsConsent.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
```

### 6.7 Routing the consent prompt after the permission gate (EDIT — additive)

The cleanest, regression-free insertion point is the **Permission** screen's `onGranted`. Today it navigates straight to `Main`. Change that single lambda in `JupiterNavHost.kt` to route through the consent screen **only when it hasn't been asked yet**. Because the NavHost itself does not read DataStore, add a tiny gating ViewModel and let the consent screen short-circuit, OR (simpler, no new VM) always route to `AnalyticsConsent` from `onGranted` and have `AnalyticsConsentScreen` auto-skip when `analyticsConsentAsked` is already true.

Implement the auto-skip in the consent screen by collecting the flag (additive change to §6.1/§6.2). Add to `AnalyticsConsentViewModel`:
```kotlin
    val alreadyAsked = settings.analyticsConsentAsked
```
And at the top of `AnalyticsConsentScreen`’s body:
```kotlin
    val asked by viewModel.alreadyAsked.collectAsStateWithLifecycle(initialValue = false)
    androidx.compose.runtime.LaunchedEffect(asked) { if (asked) onFinished() }
```
(import `androidx.lifecycle.compose.collectAsStateWithLifecycle` and `androidx.compose.runtime.getValue`.)

Then change the Permission composable's `onGranted` to:
```kotlin
                onGranted = {
                    navController.navigate(Destination.AnalyticsConsent.route) {
                        popUpTo(Destination.Permission.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
```
This is fully reversible (point `onGranted` back at `Destination.Main.route`) and never blocks the user.

### 6.8 Funnel event call sites (EDIT — additive, one line each)

Inject `Analytics` into the relevant existing `@HiltViewModel`s (constructor param `private val analytics: Analytics`) and fire fire-and-forget events. All are no-ops unless the user opted in.

- **`feature/main/MainViewModel`** (or `MainViewModel.kt` at root) — in `init { }`:
  `analytics.track(AnalyticsEvent.APP_OPENED)`
- **`feature/onboarding/OnboardingViewModel`** — in `complete()`:
  `analytics.track(AnalyticsEvent.ONBOARDING_COMPLETED)`
- **`feature/permission/PermissionViewModel`** — when access becomes granted:
  `analytics.track(AnalyticsEvent.PERMISSION_GRANTED)`
- **`feature/vault/VaultViewModel`** — in `unlock()` success path:
  `analytics.track(AnalyticsEvent.VAULT_UNLOCKED)`
- **Conversion (Initiative #1):** at the paywall show site `analytics.track(AnalyticsEvent.PAYWALL_VIEWED)`; on successful entitlement `analytics.track(AnalyticsEvent.PRO_UPGRADED, mapOf(AnalyticsProperty.TIER to "pro"))`.
- **`feature/ai/*ViewModel`** — when an AI action is invoked: `analytics.track(AnalyticsEvent.AI_USED)`
- **Remote (Initiative #2):** on connection saved: `analytics.track(AnalyticsEvent.REMOTE_CONNECTION_ADDED, mapOf(AnalyticsProperty.SOURCE to type.wireTag))` where `wireTag` is a coarse protocol bucket (`"smb"`, `"ftp"`, `"sftp"`, `"webdav"`) — never a host/path.
- **Transfer:** on transfer start: `analytics.track(AnalyticsEvent.TRANSFER_STARTED)`
- **Error reporting example** (any repository catch block that already maps to `AppError`): `analytics.recordError(domain = "transfer", message = error.displayMessage)` — pass only the already-sanitized `displayMessage`, never a raw path.

> Each call is additive. If `Analytics` is not yet injectable in a given VM, add it as a constructor param — Hilt resolves it via `AnalyticsModule`. No existing logic is modified.

---

## 7. Phase 4 — Configuration

### 7.1 Runtime configuration (no compile-time secrets)

- **Endpoint URL & write key:** stored in `SettingsDataStore` (`analyticsEndpoint`, `analyticsWriteKey`). For a stock build leave them blank → `HttpAnalyticsSink` no-ops even if bound. For QA, set them via a hidden developer field or `adb`-seeded DataStore. There is **no `BuildConfig` secret and no key checked into VCS.**
- **Switching to a real backend:** change the `@Binds` in `di/AnalyticsModule.kt` from `NoOpAnalyticsSink` to `HttpAnalyticsSink`, then configure the endpoint at runtime. Vendor-neutral targets that accept a JSON POST work directly (self-hosted collector, PostHog `/capture` self-host, a Cloud Function, etc.). Reference docs:
  - PostHog self-hosted capture API: https://posthog.com/docs/api/capture
  - Plausible events API (privacy-friendly, no cookies): https://plausible.io/docs/events-api
  - Generic: any HTTPS endpoint accepting `Content-Type: application/json`.

### 7.2 ProGuard / R8 — `app/proguard-rules.pro`

Release builds set `isMinifyEnabled = true`. Add keep rules so the event/property enums and the JSON serialization survive shrinking:
```proguard
# --- Opt-in product analytics ---
# Keep enum constants & values()/valueOf() used to build wire keys reflectively-safe.
-keepclassmembers enum com.jupiter.filemanager.core.analytics.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# org.json is part of the platform; no rule needed. OkHttp ships its own consumer rules.
```

### 7.3 No Play Console / Firebase configuration

Confirmed: no `google-services.json`, no Gradle Google Services plugin, no Firebase BoM. The build remains free of vendor SDKs.

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. `./gradlew :app:assembleDebug` → builds green.
2. Fresh install; complete onboarding and grant storage permission → **Analytics consent screen appears once**.
3. Tap **"Keep everything private"** → lands on Main. Re-open app → consent screen does **not** reappear (flag persisted).
4. Reinstall; reach consent, tap **"Share anonymous diagnostics"** → lands on Main.
5. Open **Settings** → both new toggles visible; analytics + crash both **ON** after accepting (OFF after declining).
6. Toggle analytics OFF in Settings → confirm `analyticsEnabled` flips (no crash, no UI regression to existing rows).
7. With default `NoOpAnalyticsSink` bound: enable analytics, exercise the app (open vault, start transfer) → **no network traffic** (verify with a proxy/`Logcat`), app behaves identically.
8. (Optional) Bind `HttpAnalyticsSink`, set a test endpoint via DataStore, opt in, trigger `APP_OPENED` → endpoint receives a JSON POST containing only `event`/`ts`/`properties` (assert **no path/name fields**).
9. Verify privacy default: clear app data, do **not** opt in → trigger several events → sink receives nothing (gate closed).

### 8.2 Recommended unit tests (`app/src/test/...`)

- `GatedAnalyticsTest` — with `analyticsEnabled=false`, `track()` never calls the sink; with `true`, it does. Use a fake `AnalyticsSink` recording payloads, `StandardTestDispatcher`, and a fake/seeded `SettingsDataStore` (or interface seam). Assert `recordError` respects the **separate** `crashReportingEnabled` gate.
- `AnalyticsPayloadTest` — building a payload from every `AnalyticsEvent` yields the expected stable `key` and contains no path-like fields.
- `HttpAnalyticsSinkTest` (optional, needs `mockwebserver`) — blank endpoint ⇒ `AppResult.Success` with **zero** requests; configured endpoint ⇒ exactly one POST with JSON body; HTTP 500 ⇒ `AppResult.Failure(AppError.Io)`; `IOException` ⇒ `AppResult.Failure`.
- `AnalyticsConsentViewModelTest` — `accept()` sets both flags true + `consentAsked` true and tracks `ANALYTICS_OPT_IN`; `decline()` sets both false + `consentAsked` true and tracks nothing.

### 8.3 Instrumented test (optional)

- `AnalyticsConsentScreenTest` (Compose UI) — both buttons render; clicking declines/accepts invokes `onFinished`. Use a Hilt test rule binding `NoOpAnalytics`.

Run: `./gradlew :app:testDebugUnitTest`.

---

## 9. Error handling & edge cases

1. **Sink network failure (timeout/`IOException`).** `HttpAnalyticsSink` catches `IOException` and returns `AppResult.Failure(AppError.Io)`; `GatedAnalytics` swallows the failure inside `runCatching` so no feature thread is affected. App continues normally.
2. **DataStore read error while checking consent.** Both `SettingsDataStore.analyticsEnabled`/`crashReportingEnabled` use the existing `.safe()` operator that emits `emptyPreferences()` on `IOException`, falling back to `false` (closed gate). `GatedAnalytics.isEnabled` additionally wraps the `runBlocking` read in `runCatching { }.getOrDefault(false)`.
3. **Blank endpoint but user opted in.** `HttpAnalyticsSink.send` returns `AppResult.Success(Unit)` immediately without any request — behaves exactly like `NoOpAnalyticsSink`, preserving the privacy promise even on a misconfigured build.
4. **HTTP non-2xx (e.g. 401 bad write key, 500 server error).** Returned as `AppResult.Failure(AppError.Io("...HTTP code"))`, swallowed by the gate; never retried aggressively (single attempt, fire-and-forget) so no battery/network storm.
5. **Event fired before Hilt graph ready / from a Compose preview.** Previews bind `NoOpAnalytics`; production `GatedAnalytics` is a `@Singleton` injected into VMs that only exist after the graph is built, so `track()` cannot run pre-graph. `recordError`/`track` are non-suspending and never throw regardless.
6. **Concurrent rapid events.** `GatedAnalytics` dispatches each on a `SupervisorJob`-backed `CoroutineScope(io)`; one failing job cannot cancel siblings, and there is no shared mutable state, so it is thread-safe under burst.
7. **User opts out mid-session.** Each `track`/`recordError` re-reads the flag via `settings.*.first()` at emission time, so a freshly toggled-off state immediately suppresses further emission — no stale in-memory cache.
8. **Consent prompt re-entrancy.** `analyticsConsentAsked` persists after the first decision and the screen's `LaunchedEffect` auto-skips when true, so the prompt shows at most once and never blocks navigation.
9. **Property misuse guard.** Property keys are constrained to the `AnalyticsProperty` enum; values must be coarse tokens. Reviewers reject any call passing `item.path`/`item.name`. (Optionally add a debug `require(value.length < 32)` assertion — omitted from production for fail-safety.)

---

## 10. Integration with other initiatives

This initiative is the **measurement substrate** for the whole portfolio; it depends on none of them to build, and additively instruments several:

- **#1 Pro monetization** — provides `PAYWALL_VIEWED` / `PRO_UPGRADED` conversion events; the single most valuable signal for tuning pricing. Add the two `track` calls at #1's paywall and entitlement sites.
- **#2 Cloud OAuth / Remote** — emits `REMOTE_CONNECTION_ADDED` with a coarse `SOURCE` protocol bucket; never sends host/credentials.
- **#3 i18n** — consent + settings strings already externalized via `strings.xml` so they localize automatically.
- **#4 AI Pro suite** — emits `AI_USED`; reuses the exact OkHttp-on-`@IoDispatcher` + `AppResult` pattern from `AnthropicAiAssistant`, so the sink and assistant are architecturally consistent.
- **#5 Widgets/Shortcuts/Tiles** — entry-point events can be added later with new enum constants; no structural change needed.
- **Ordering:** ship #6 early (it is tiny and additive) so #1–#5 can land their one-line event calls as they merge. No initiative is *blocked* by #6; they are merely *better measured* with it.

---

## 11. Rollback plan

The feature is **purely additive and behind a default-OFF gate**, so rollback is low-risk and incremental:

1. **Soft disable (no code removal):** in `di/AnalyticsModule.kt` change `bindAnalytics` to bind `NoOpAnalytics` instead of `GatedAnalytics`. Every `track`/`recordError` call becomes inert immediately; call sites compile unchanged.
2. **Hide UI:** remove the two `Switch` rows from `SettingsScreen.kt` and point `Destination.Permission`'s `onGranted` back to `Destination.Main.route`; delete the `AnalyticsConsent` composable + destination. The new DataStore keys simply lie dormant (defaults).
3. **Full removal:** delete `core/analytics/`, `feature/consent/`, `di/AnalyticsModule.kt`; revert the additive blocks in `SettingsDataStore.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `Destinations.kt`, `JupiterNavHost.kt`, the manifest `INTERNET` line, the ProGuard block, and the one-line event calls in funnel VMs. Because each edit is isolated and additive, `git revert` of the feature commit restores the prior tree with no cross-cutting fallout. No schema migration is required (DataStore keys are forward/backward tolerant).

---

## 12. Definition of done

- [ ] `core/analytics/` created: `Analytics`, `AnalyticsEvent`/`AnalyticsProperty`, `AnalyticsSink`/`AnalyticsPayload`, `NoOpAnalyticsSink`, `HttpAnalyticsSink`, `GatedAnalytics`, `NoOpAnalytics` — all compile, no PII fields anywhere.
- [ ] `di/AnalyticsModule.kt` binds `Analytics -> GatedAnalytics` and `AnalyticsSink -> NoOpAnalyticsSink` (default emits nothing).
- [ ] `SettingsDataStore` extended with `analyticsEnabled`/`crashReportingEnabled`/`analyticsConsentAsked`/`analyticsEndpoint`/`analyticsWriteKey`; **all default to disabled/blank**; existing keys untouched.
- [ ] `feature/consent/` consent screen + `@HiltViewModel` added; immutable choices persisted; opt-in/opt-out tracked correctly; prompt shows at most once.
- [ ] `Destination.AnalyticsConsent` added and wired in `JupiterNavHost`; Permission→Consent→Main flow works and auto-skips when already asked.
- [ ] Settings screen shows both toggles, reflecting and mutating persisted state; no layout/behaviour change to pre-existing settings rows.
- [ ] At least the funnel events `APP_OPENED`, `ONBOARDING_COMPLETED`, `PERMISSION_GRANTED`, `VAULT_UNLOCKED` are emitted via injected `Analytics` (one-line, fail-safe).
- [ ] `INTERNET` permission present in `AndroidManifest.xml`; ProGuard keep rule for `core.analytics` enums added.
- [ ] **Privacy invariant verified:** with default sink and/or no consent, exercising the app produces **zero** network requests and zero stored events.
- [ ] **No regression:** existing **Settings** preferences (theme/show-hidden/dual-pane/AI key) still load and persist correctly; **Onboarding → Permission → Main** flow still completes for a fresh install.
- [ ] **No regression:** existing **Storage Analytics** (`feature/analytics`) and **Vault unlock** features still work and are unaffected by the new `core/analytics` package (no symbol clash).
- [ ] Recommended unit tests added and green: `GatedAnalyticsTest`, `AnalyticsConsentViewModelTest` (sink/network tests optional).
- [ ] **CI green:** `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both pass.
