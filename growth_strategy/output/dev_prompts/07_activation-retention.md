# Initiative #7 — Activation & Retention Loop (Onboarding Funnel, What's-New, Rating, Re-engagement)

> **Dev-prompt for an autonomous Android coding agent.** Implement this initiative end-to-end against the **real Jupiter codebase** at `/home/user/Jupiter`. Work **additively** and **without regression** to any existing working feature. Produce complete, valid Kotlin — no pseudocode, no TODOs left dangling. Do not ask follow-up questions; everything you need is below.

---

## 1. Initiative header

- **Title:** Activation & Retention Loop — Onboarding Funnel instrumentation, What's-New sheet on version bump, in-app review prompt, and an opt-in WorkManager re-engagement notification.
- **Value range:** **+€90k–€190k** (incremental annual contribution from improved Day-1 activation and Day-7/Day-30 retention feeding the Pro funnel of initiative #1).
- **Business case:** Jupiter already ships a polished four-panel onboarding pager, but it is *uninstrumented* — we cannot see where users drop, and we never bring lapsed users back. This initiative closes the loop with four additive, low-risk mechanisms: (1) **funnel instrumentation** that records which onboarding step a user reached and whether they completed vs. skipped, so product can A/B and tighten copy; (2) a **What's-New bottom sheet** that fires exactly once per version bump to re-surface features after an update (re-activation without a server); (3) an **in-app review prompt** fired at a *positive moment* (e.g. after a successful cleanup or N successful sessions) using the official Play In-App Review API, maximising rating volume and store conversion; and (4) an **opt-in re-engagement notification** scheduled via WorkManager that nudges idle users with a concrete value hook ("X GB reclaimable"). Each mechanism is independently shippable, fully gated behind persisted flags in `AppStateDataStore`, and degrades silently when unavailable. The expected uplift is a few percentage points of activation and retention, which compounds directly into Pro trial starts.

---

## 2. Codebase context

Package root: `com.jupiter.filemanager` → `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`.

### Relevant real files that EXIST today

```
app/src/main/java/com/jupiter/filemanager/
├── JupiterApp.kt                         # @HiltAndroidApp + Configuration.Provider (HiltWorkerFactory). REUSE for WorkManager.
├── MainActivity.kt                       # single-activity host; sets JupiterNavHost(startDestination = Splash). NEEDS small change (review trigger host is fine in VM).
├── core/result/
│   ├── AppResult.kt                      # sealed AppResult<Success|Failure>; onSuccess/onFailure/getOrNull/map. REUSE.
│   └── AppError.kt                       # sealed AppError (PermissionDenied/NotFound/Io/Unknown…). EXTEND? No — reuse Unknown/Io.
├── di/
│   ├── CoroutineModule.kt                # @IoDispatcher / @DefaultDispatcher / @MainDispatcher qualifiers. REUSE @IoDispatcher.
│   └── FeatureRepositoryModule.kt        # @Binds module for feature repos. PATTERN to copy for new bindings.
├── data/preferences/
│   ├── AppStateDataStore.kt              # Preferences DataStore "jupiter_app_state"; onboardingCompleted flag. NEEDS CHANGE (add keys).
│   └── SettingsDataStore.kt             # user settings store "jupiter_settings". Leave untouched.
├── data/automation/
│   └── AutomationWorker.kt               # @HiltWorker CoroutineWorker example. PATTERN to copy for ReengagementWorker.
├── feature/onboarding/
│   ├── OnboardingScreen.kt               # HorizontalPager, Skip / Next / Get Started. NEEDS CHANGE (emit funnel events).
│   └── OnboardingViewModel.kt            # complete() → appState.setOnboardingCompleted(true). NEEDS CHANGE (record funnel).
├── feature/splash/
│   ├── SplashScreen.kt                   # brand splash; calls onFinished(route). Leave untouched.
│   └── SplashViewModel.kt                # routes Onboarding/Permission/Main. Leave untouched.
├── feature/home/
│   ├── HomeScreen.kt                     # dashboard. NEEDS CHANGE (host What's-New sheet + review/reengagement hooks via VM).
│   ├── HomeUiState.kt                    # immutable UiState. NEEDS CHANGE (add whatsNew + review flags).
│   └── HomeViewModel.kt                  # @HiltViewModel. NEEDS CHANGE (inject new managers, drive sheet/review/schedule).
├── feature/automation/
│   └── AutomationViewModel.kt            # shows WorkManager.getInstance(context).enqueueUniqueWork(...) pattern. REFERENCE.
├── domain/repository/
│   └── StorageAnalyticsRepository.kt     # storageOverview(): AppResult<StorageOverview>. REUSE for "GB reclaimable".
└── ui/navigation/
    ├── Destinations.kt                   # sealed Destination(route). NEEDS CHANGE (add WhatsNew if routed; we use a sheet, so optional).
    └── JupiterNavHost.kt                 # NavHost wiring. NEEDS CHANGE only if WhatsNew is a destination (we keep it a sheet → no change).
```

Build config that EXISTS:
```
build.gradle.kts            (root)
app/build.gradle.kts        # plugins: android.application, kotlin.android, kotlin.compose, ksp, hilt. buildConfig=true.
gradle/libs.versions.toml   # version catalog. workManager=2.10.0 already present.
app/src/main/AndroidManifest.xml   # POST_NOTIFICATIONS already declared. No review/notification channel yet.
app/proguard-rules.pro      # release minify rules.
```

### What is MISSING (you will create)

```
feature/whatsnew/
├── WhatsNewSheet.kt                 # NEW — Material3 ModalBottomSheet listing version highlights.
├── WhatsNewContent.kt              # NEW — versioned highlight catalog (pure data, no I/O).
data/activation/
├── ActivationManager.kt           # NEW — orchestrates onboarding-funnel recording, what's-new gating, review eligibility, reengagement scheduling.
├── InAppReviewManager.kt          # NEW — wraps com.google.android.play:review-ktx.
├── ReengagementScheduler.kt       # NEW — enqueues/cancels the periodic WorkManager job.
├── ReengagementWorker.kt          # NEW — @HiltWorker; computes reclaimable bytes, posts notification.
├── ReengagementNotifier.kt        # NEW — builds notification channel + notification.
└── OnboardingFunnelEvent.kt       # NEW — enum of funnel steps.
di/
└── ActivationModule.kt            # NEW — @Provides WorkManager + @Binds activation interfaces.
```

> **Key architectural decision:** What's-New is rendered as a **ModalBottomSheet hosted inside `HomeScreen`**, *not* a navigation destination. This avoids touching `JupiterNavHost`/`Destinations` routing (lower regression risk) and means the sheet appears on the first frame of the dashboard after a version bump. The review prompt and reengagement scheduling are driven from `HomeViewModel` because Home is the first interactive screen every session reaches.

---

## 3. Pre-conditions

### Gradle dependencies to add (exact coordinates)

| Purpose | Maven coordinate | Catalog alias |
|---|---|---|
| Play In-App Review (Kotlin) | `com.google.android.play:review-ktx:2.0.2` | `play-review-ktx` |
| (already present) WorkManager | `androidx.work:work-runtime-ktx:2.10.0` | `androidx-work-runtime-ktx` |
| (already present) Hilt Work | `androidx.hilt:hilt-work:1.2.0` | `hilt-work` |

`review-ktx` resolves from **Google's Maven** (`google()`), which is already in `settings.gradle.kts` `dependencyResolutionManagement`. The `google()` content filter in `pluginManagement` does not apply to dependency resolution, so no filter change is needed.

### Manifest / permission prerequisites

- `android.permission.POST_NOTIFICATIONS` is **already declared** in `AndroidManifest.xml` (line 36). The reengagement notification reuses it. On API 33+ you must obtain the **runtime** grant before posting; we gate the opt-in toggle behind a runtime permission request (Phase 3).
- No new manifest permission is required.
- A notification channel (`jupiter_reengagement`) must be created at runtime (API 26+; `minSdk=26` so always). Done in `ReengagementNotifier`.

### Play Console prerequisites (for review prompt to actually surface)

- In-App Review only shows a dialog when the app is installed via Google Play and the user is eligible (Play enforces an opaque quota; it may **no-op** in debug/sideload). Code must therefore **never block** on it and must treat a no-op as success.
- No API key, no `google-services.json`, no Firebase needed. The Review API is a Play Core library call only.

### No external server / no env keys

This entire initiative is **client-only**. There is no backend, no analytics SDK, no API key. Funnel events are persisted locally in DataStore (and structured so a future analytics initiative can forward them). This keeps the initiative self-contained and CI-safe.

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Add to `[versions]`:

```toml
playReview = "2.0.2"
```

Add to `[libraries]`:

```toml
play-review-ktx = { group = "com.google.android.play", name = "review-ktx", version.ref = "playReview" }
```

### 4.2 `app/build.gradle.kts`

In the `dependencies { }` block, after the `// Background work` group, add:

```kotlin
    // In-app review (Play Core) — drives the rating prompt at a positive moment.
    implementation(libs.play.review.ktx)
```

No plugin changes. `buildConfig = true` is already set, so `BuildConfig.VERSION_CODE` / `BuildConfig.VERSION_NAME` are available for version-bump detection.

### 4.3 `AndroidManifest.xml`

No edits required — `POST_NOTIFICATIONS` already present and WorkManager initialises via the existing `JupiterApp` `Configuration.Provider`. (If your lint config flags an unused `tools:targetApi`, leave as is.)

### 4.4 Resources — `app/src/main/res/values/strings.xml`

Append (create file/keys if absent; do not remove existing strings):

```xml
    <!-- Re-engagement notification (initiative #7) -->
    <string name="reengage_channel_name">Storage reminders</string>
    <string name="reengage_channel_desc">Occasional reminders when space can be reclaimed.</string>
    <string name="reengage_title">Free up space on your device</string>
    <string name="reengage_body">About %1$s can be reclaimed. Tap to clean up now.</string>
    <string name="reengage_body_generic">Tap to review files you can clean up.</string>

    <!-- What's-New sheet -->
    <string name="whatsnew_title">What\'s new</string>
    <string name="whatsnew_dismiss">Got it</string>
```

---

## 5. Phase 2 — Data / domain layer

All new files live under `com.jupiter.filemanager.data.activation`. Every cross-boundary failure is mapped to `AppResult`/`AppError`; all blocking work runs on `@IoDispatcher`.

### 5.1 `data/preferences/AppStateDataStore.kt` — EXTEND (additive, full replacement)

Add new keys and accessors. **Do not remove** the existing `onboardingCompleted` API — `SplashViewModel`/`OnboardingViewModel` depend on it.

```kotlin
package com.jupiter.filemanager.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.appStateDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_app_state")

@Singleton
class AppStateDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dataStore: DataStore<Preferences> = context.appStateDataStore

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

        // ---- Activation & retention (initiative #7) ----
        // Furthest onboarding page index reached (0-based); -1 = never started.
        val ONBOARDING_FURTHEST_STEP = intPreferencesKey("onboarding_furthest_step")
        // How the funnel ended: "skipped" | "completed" | "" (in-progress/unknown).
        val ONBOARDING_OUTCOME = stringPreferencesKey("onboarding_outcome")
        // versionCode whose What's-New sheet was last shown; 0 = never.
        val WHATS_NEW_SHOWN_VERSION = intPreferencesKey("whats_new_shown_version")
        // Count of "positive moments" accrued toward the review prompt.
        val POSITIVE_MOMENT_COUNT = intPreferencesKey("positive_moment_count")
        // Whether the review prompt has already been requested (one-shot guard).
        val REVIEW_REQUESTED = booleanPreferencesKey("review_requested")
        // Whether the user opted in to re-engagement notifications.
        val REENGAGE_OPT_IN = booleanPreferencesKey("reengage_opt_in")
        // Epoch millis of the last foreground session, for idle computation.
        val LAST_ACTIVE_AT = longPreferencesKey("last_active_at")
    }

    val onboardingCompleted: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETED] = value }
    }

    // ---- Onboarding funnel ----

    val onboardingFurthestStep: Flow<Int> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ONBOARDING_FURTHEST_STEP] ?: -1 }

    /** Monotonically records the furthest onboarding page reached (never regresses). */
    suspend fun recordOnboardingStep(stepIndex: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.ONBOARDING_FURTHEST_STEP] ?: -1
            if (stepIndex > current) prefs[Keys.ONBOARDING_FURTHEST_STEP] = stepIndex
        }
    }

    val onboardingOutcome: Flow<String> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.ONBOARDING_OUTCOME] ?: "" }

    suspend fun setOnboardingOutcome(outcome: String) {
        dataStore.edit { prefs -> prefs[Keys.ONBOARDING_OUTCOME] = outcome }
    }

    // ---- What's-New ----

    val whatsNewShownVersion: Flow<Int> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.WHATS_NEW_SHOWN_VERSION] ?: 0 }

    suspend fun setWhatsNewShownVersion(versionCode: Int) {
        dataStore.edit { prefs -> prefs[Keys.WHATS_NEW_SHOWN_VERSION] = versionCode }
    }

    // ---- Review prompt ----

    val positiveMomentCount: Flow<Int> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.POSITIVE_MOMENT_COUNT] ?: 0 }

    /** Atomically increments the positive-moment counter and returns the new value. */
    suspend fun incrementPositiveMoments(): Int {
        var updated = 0
        dataStore.edit { prefs ->
            updated = (prefs[Keys.POSITIVE_MOMENT_COUNT] ?: 0) + 1
            prefs[Keys.POSITIVE_MOMENT_COUNT] = updated
        }
        return updated
    }

    val reviewRequested: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.REVIEW_REQUESTED] ?: false }

    suspend fun setReviewRequested(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.REVIEW_REQUESTED] = value }
    }

    // ---- Re-engagement ----

    val reengageOptIn: Flow<Boolean> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.REENGAGE_OPT_IN] ?: false }

    suspend fun setReengageOptIn(value: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.REENGAGE_OPT_IN] = value }
    }

    val lastActiveAt: Flow<Long> = dataStore.data
        .safe()
        .map { prefs -> prefs[Keys.LAST_ACTIVE_AT] ?: 0L }

    suspend fun setLastActiveAt(epochMillis: Long) {
        dataStore.edit { prefs -> prefs[Keys.LAST_ACTIVE_AT] = epochMillis }
    }

    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) {
            emit(androidx.datastore.preferences.core.emptyPreferences())
        } else {
            throw throwable
        }
    }
}
```

### 5.2 `data/activation/OnboardingFunnelEvent.kt` — NEW

```kotlin
package com.jupiter.filemanager.data.activation

/**
 * Discrete steps in the onboarding funnel, recorded locally so product can later
 * see drop-off without any server. Ordinal order matches display order.
 */
enum class OnboardingFunnelEvent(val stepIndex: Int) {
    PAGE_VIEWED_0(0),
    PAGE_VIEWED_1(1),
    PAGE_VIEWED_2(2),
    PAGE_VIEWED_3(3),
    SKIPPED(-1),
    COMPLETED(99);

    companion object {
        const val OUTCOME_SKIPPED = "skipped"
        const val OUTCOME_COMPLETED = "completed"
    }
}
```

### 5.3 `data/activation/ActivationManager.kt` — NEW

Central orchestrator. Pure suspend functions, no Android UI. Injected into ViewModels.

```kotlin
package com.jupiter.filemanager.data.activation

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.StorageCategory
import com.jupiter.filemanager.domain.repository.StorageAnalyticsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the activation & retention loop: onboarding-funnel recording,
 * What's-New gating on version bump, in-app-review eligibility, and the
 * reclaimable-bytes estimate used by both the review trigger and the
 * re-engagement notification.
 *
 * Holds no UI; every method is a suspend function safe to call from a ViewModel
 * scope. Reads/writes go through [AppStateDataStore]; heavier estimates run on
 * [IoDispatcher].
 */
@Singleton
class ActivationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appState: AppStateDataStore,
    private val analyticsRepository: StorageAnalyticsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Number of positive moments required before the review prompt is offered. */
    private val reviewThreshold = 3

    // ---------- Onboarding funnel ----------

    suspend fun recordFunnel(event: OnboardingFunnelEvent) {
        when (event) {
            OnboardingFunnelEvent.SKIPPED ->
                appState.setOnboardingOutcome(OnboardingFunnelEvent.OUTCOME_SKIPPED)
            OnboardingFunnelEvent.COMPLETED ->
                appState.setOnboardingOutcome(OnboardingFunnelEvent.OUTCOME_COMPLETED)
            else -> appState.recordOnboardingStep(event.stepIndex)
        }
    }

    // ---------- What's-New ----------

    /**
     * Returns true exactly once per version bump: when the current [versionCode]
     * is newer than the version whose sheet was last shown, AND onboarding is
     * already complete (so first-run users see onboarding, not What's-New).
     * Marking-as-shown is the caller's job via [markWhatsNewShown].
     */
    suspend fun shouldShowWhatsNew(versionCode: Int): Boolean {
        val onboardingDone = appState.onboardingCompleted.first()
        if (!onboardingDone) return false
        val lastShown = appState.whatsNewShownVersion.first()
        return versionCode > lastShown && lastShown != 0
        // lastShown == 0 means a fresh install: seed it (markWhatsNewShown) without
        // showing, so an installed-at-vN user is not greeted by a What's-New on first run.
    }

    /** Seeds the shown-version on first install so the sheet never fires retroactively. */
    suspend fun seedWhatsNewIfFresh(versionCode: Int) {
        if (appState.whatsNewShownVersion.first() == 0) {
            appState.setWhatsNewShownVersion(versionCode)
        }
    }

    suspend fun markWhatsNewShown(versionCode: Int) {
        appState.setWhatsNewShownVersion(versionCode)
    }

    // ---------- Review prompt ----------

    /**
     * Records a positive moment and returns true if, as a result, the app should
     * now offer the in-app review (threshold reached and not yet requested).
     */
    suspend fun registerPositiveMomentAndCheck(): Boolean {
        if (appState.reviewRequested.first()) return false
        val count = appState.incrementPositiveMoments()
        return count >= reviewThreshold
    }

    suspend fun markReviewRequested() {
        appState.setReviewRequested(true)
    }

    // ---------- Reclaimable estimate ----------

    /**
     * Estimates how many bytes the user could plausibly reclaim, derived from the
     * real storage overview (cache + downloads + other categories considered
     * "cleanable"). Wrapped in [AppResult]; never throws.
     */
    suspend fun estimateReclaimableBytes(): AppResult<Long> = withContext(ioDispatcher) {
        try {
            when (val overview = analyticsRepository.storageOverview()) {
                is AppResult.Success -> {
                    val cleanable = overview.data.categories
                        .filter { it.category in CLEANABLE_CATEGORIES }
                        .sumOf { it.sizeBytes }
                    AppResult.Success(cleanable)
                }
                is AppResult.Failure -> overview
            }
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Unknown("Could not estimate reclaimable space", t))
        }
    }

    // ---------- Session ----------

    suspend fun touchSession(nowMillis: Long) {
        appState.setLastActiveAt(nowMillis)
    }

    private companion object {
        // Categories we treat as plausibly reclaimable for the nudge headline.
        val CLEANABLE_CATEGORIES = setOf(
            StorageCategory.DOWNLOADS,
            StorageCategory.OTHER,
        )
    }
}
```

> **Note on `StorageCategory`:** Use only members that exist in `com.jupiter.filemanager.domain.model.StorageCategory`. `DOWNLOADS` and `OTHER` are referenced by `HomeViewModel` already, so they are guaranteed present. If a `CACHE` member exists in that enum, add it to `CLEANABLE_CATEGORIES`; otherwise leave the set as-is. Do **not** invent enum members.

### 5.4 `data/activation/InAppReviewManager.kt` — NEW

```kotlin
package com.jupiter.filemanager.data.activation

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.review.testing.FakeReviewManager
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Thin wrapper over the Play In-App Review API.
 *
 * The flow is best-effort: Play may legitimately no-op (debug build, quota,
 * sideload). Both [requestReview] failure and a successful-but-invisible flow
 * are treated identically — there is no user-facing error, by Google's design.
 */
@Singleton
class InAppReviewManager @Inject constructor() {

    /**
     * Requests and launches the review flow against [activity], suspending until
     * Play reports the flow finished. Returns true if the flow ran to completion
     * (whether or not a dialog was actually shown), false if it could not start.
     */
    suspend fun launchReview(activity: Activity): Boolean {
        val manager = ReviewManagerFactory.create(activity.applicationContext)
        val reviewInfo = suspendCancellableCoroutine<Any?> { cont ->
            manager.requestReviewFlow().addOnCompleteListener { task ->
                if (task.isSuccessful) cont.resume(task.result) else cont.resume(null)
            }
        } ?: return false

        @Suppress("UNCHECKED_CAST")
        val info = reviewInfo as com.google.android.play.core.review.ReviewInfo
        return suspendCancellableCoroutine { cont ->
            manager.launchReviewFlow(activity, info).addOnCompleteListener {
                // launchReviewFlow always reports success once the flow finishes,
                // even when Play decided not to display a card.
                cont.resume(true)
            }
        }
    }
}
```

### 5.5 `data/activation/ReengagementNotifier.kt` — NEW

```kotlin
package com.jupiter.filemanager.data.activation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jupiter.filemanager.MainActivity
import com.jupiter.filemanager.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Creates the re-engagement notification channel and posts the nudge.
 *
 * Posting is guarded against a missing POST_NOTIFICATIONS grant (API 33+):
 * [NotificationManagerCompat.notify] is wrapped so a denied permission never
 * crashes the worker.
 */
@Singleton
class ReengagementNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reengage_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.reengage_channel_desc)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts the nudge. [reclaimableLabel] is a pre-formatted size string ("1.2 GB")
     * or null when no concrete figure is available (falls back to generic copy).
     */
    fun postReengagement(reclaimableLabel: String?) {
        ensureChannel()

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (reclaimableLabel != null) {
            context.getString(R.string.reengage_body, reclaimableLabel)
        } else {
            context.getString(R.string.reengage_body_generic)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle(context.getString(R.string.reengage_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip; the opt-in toggle
            // should have requested it, but a revoked grant must not crash.
        }
    }

    private companion object {
        const val CHANNEL_ID = "jupiter_reengagement"
        const val NOTIFICATION_ID = 7001
        const val REQUEST_CODE = 7002
    }
}
```

> Replace `android.R.drawable.ic_menu_delete` with an existing app drawable if one is clearly a notification-suitable monochrome icon (e.g. a vector under `res/drawable`). Using the platform drawable guarantees the build never fails on a missing resource.

### 5.6 `data/activation/ReengagementWorker.kt` — NEW

Mirrors `AutomationWorker` exactly (HiltWorker pattern, never crashes the process).

```kotlin
package com.jupiter.filemanager.data.activation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.core.util.formatBytes
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Periodic background worker that posts the opt-in re-engagement nudge.
 *
 * Constructed by Hilt's HiltWorkerFactory (wired in [com.jupiter.filemanager.JupiterApp]).
 * It re-checks the opt-in flag at run time (a user may have toggled it off after
 * the job was scheduled), estimates reclaimable bytes, and posts the notification.
 * Any failure is swallowed into [Result.success] so a bad run never disables the
 * periodic chain or crashes the process.
 */
@HiltWorker
class ReengagementWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val appState: AppStateDataStore,
    private val activationManager: ActivationManager,
    private val notifier: ReengagementNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        if (!appState.reengageOptIn.first()) {
            // User opted out after scheduling — do nothing, keep periodic chain alive.
            Result.success()
        } else {
            val label = when (val estimate = activationManager.estimateReclaimableBytes()) {
                is AppResult.Success -> if (estimate.data > MIN_RECLAIMABLE_BYTES) {
                    formatBytes(estimate.data)
                } else {
                    null // too little to bother quoting a figure
                }
                is AppResult.Failure -> null
            }
            notifier.postReengagement(label)
            Result.success()
        }
    } catch (_: Exception) {
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "reengagement_periodic"
        private const val MIN_RECLAIMABLE_BYTES: Long = 200L * 1024 * 1024 // 200 MB
    }
}
```

> `formatBytes` lives at `com.jupiter.filemanager.core.util.formatBytes` (already imported by `HomeScreen.kt`). Reuse it; do not reimplement.

### 5.7 `data/activation/ReengagementScheduler.kt` — NEW

```kotlin
package com.jupiter.filemanager.data.activation

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues/cancels the periodic re-engagement job. Uses a *unique* periodic work
 * name with KEEP policy so toggling on repeatedly never stacks duplicate chains.
 */
@Singleton
class ReengagementScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun schedule() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()

        val request = PeriodicWorkRequestBuilder<ReengagementWorker>(
            REPEAT_INTERVAL_DAYS, TimeUnit.DAYS,
            FLEX_HOURS, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setInitialDelay(INITIAL_DELAY_DAYS, TimeUnit.DAYS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            ReengagementWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(ReengagementWorker.UNIQUE_WORK_NAME)
    }

    private companion object {
        const val REPEAT_INTERVAL_DAYS: Long = 3
        const val FLEX_HOURS: Long = 6
        const val INITIAL_DELAY_DAYS: Long = 3
    }
}
```

### 5.8 `di/ActivationModule.kt` — NEW

```kotlin
package com.jupiter.filemanager.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the application [WorkManager] singleton for the activation/retention
 * feature. All other activation classes (ActivationManager, InAppReviewManager,
 * ReengagementNotifier, ReengagementScheduler) have @Inject constructors and need
 * no @Provides/@Binds.
 *
 * Separate from existing modules; does not modify them.
 */
@Module
@InstallIn(SingletonComponent::class)
object ActivationModule {

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
```

> `AutomationViewModel` calls `WorkManager.getInstance(context)` directly; that still works because `getInstance` returns the same singleton this module provides. No conflict.

---

## 6. Phase 3 — Presentation

### 6.1 `feature/whatsnew/WhatsNewContent.kt` — NEW

```kotlin
package com.jupiter.filemanager.feature.whatsnew

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Lock

/** A single highlighted change shown in the What's-New sheet. */
data class WhatsNewHighlight(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

/**
 * Static, versioned catalog of release highlights. Update this list with each
 * release; the most recent set is shown when the version code bumps.
 * Pure data — no I/O, trivially unit-testable.
 */
object WhatsNewCatalog {

    val current: List<WhatsNewHighlight> = listOf(
        WhatsNewHighlight(
            icon = Icons.Rounded.CleaningServices,
            title = "Smarter cleanup",
            description = "Reclaim space faster with refreshed duplicate and large-file tools.",
        ),
        WhatsNewHighlight(
            icon = Icons.Rounded.Lock,
            title = "Vault improvements",
            description = "Your private files stay locked and load quicker than before.",
        ),
        WhatsNewHighlight(
            icon = Icons.Rounded.AutoAwesome,
            title = "Polish everywhere",
            description = "Dozens of fixes and refinements across the app.",
        ),
    )
}
```

### 6.2 `feature/whatsnew/WhatsNewSheet.kt` — NEW

```kotlin
package com.jupiter.filemanager.feature.whatsnew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.R

/**
 * Modal bottom sheet listing the highlights of the just-installed version.
 * Fully stateless: shown only when the host decides; [onDismiss] persists the
 * "shown" flag so it never reappears for this version.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(
    highlights: List<WhatsNewHighlight>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.whatsnew_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(modifier = Modifier.height(20.dp))

            highlights.forEach { highlight ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = highlight.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Column {
                        Text(
                            text = highlight.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = highlight.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(text = stringResource(R.string.whatsnew_dismiss))
            }
        }
    }
}
```

### 6.3 `feature/onboarding/OnboardingViewModel.kt` — REPLACE (additive funnel recording)

```kotlin
package com.jupiter.filemanager.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupiter.filemanager.data.activation.ActivationManager
import com.jupiter.filemanager.data.activation.OnboardingFunnelEvent
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the onboarding flow. Persists completion (unchanged contract used by the
 * splash router) and, additively, records funnel events so product can see
 * which step users reach and whether they skip or complete.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appState: AppStateDataStore,
    private val activationManager: ActivationManager,
) : ViewModel() {

    /** Records that the user viewed [pageIndex] (monotonic furthest-step). */
    fun onPageViewed(pageIndex: Int) {
        viewModelScope.launch {
            val event = when (pageIndex) {
                0 -> OnboardingFunnelEvent.PAGE_VIEWED_0
                1 -> OnboardingFunnelEvent.PAGE_VIEWED_1
                2 -> OnboardingFunnelEvent.PAGE_VIEWED_2
                else -> OnboardingFunnelEvent.PAGE_VIEWED_3
            }
            activationManager.recordFunnel(event)
        }
    }

    /** Marks onboarding completed; [skipped] distinguishes Skip from finishing. */
    fun complete(skipped: Boolean) {
        viewModelScope.launch {
            activationManager.recordFunnel(
                if (skipped) OnboardingFunnelEvent.SKIPPED else OnboardingFunnelEvent.COMPLETED,
            )
            appState.setOnboardingCompleted(true)
        }
    }
}
```

### 6.4 `feature/onboarding/OnboardingScreen.kt` — surgical edits

Only three changes; everything else stays. **(a)** Replace the `finish()` helper and the Skip/last-page call sites so they pass the `skipped` flag, and **(b)** report page views.

Replace the `finish()` function (lines ~74-77) with:

```kotlin
    fun finish(skipped: Boolean) {
        viewModel.complete(skipped = skipped)
        onFinished()
    }
```

Add a page-view reporter right after `val isLastPage = ...` (~line 72):

```kotlin
    androidx.compose.runtime.LaunchedEffect(pagerState.currentPage) {
        viewModel.onPageViewed(pagerState.currentPage)
    }
```

Update the Skip button (`TextButton(onClick = ::finish)`):

```kotlin
                if (!isLastPage) {
                    TextButton(onClick = { finish(skipped = true) }) {
                        Text(text = "Skip")
                    }
                }
```

Update the primary button `onClick` last-page branch:

```kotlin
                onClick = {
                    if (isLastPage) {
                        finish(skipped = false)
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
```

No other lines in `OnboardingScreen.kt` change.

### 6.5 `feature/home/HomeUiState.kt` — EXTEND (additive fields)

Add to the `HomeUiState` data class (keep all existing fields):

```kotlin
    /** Whether the What's-New sheet should be visible (version bump detected). */
    val showWhatsNew: Boolean = false,
    /** Highlights to render in the What's-New sheet. */
    val whatsNewHighlights: List<com.jupiter.filemanager.feature.whatsnew.WhatsNewHighlight> = emptyList(),
    /** One-shot signal: the app should launch the in-app review flow now. */
    val launchReview: Boolean = false,
```

### 6.6 `feature/home/HomeViewModel.kt` — EXTEND (constructor + new logic)

Add the new injected dependencies and an `init`-time activation check. Keep **all** existing fields/methods. Add `BuildConfig` import.

Constructor — add three parameters:

```kotlin
    private val activationManager: ActivationManager,
    private val reengagementScheduler: ReengagementScheduler,
    private val appState: AppStateDataStore,
```

Add imports:

```kotlin
import com.jupiter.filemanager.BuildConfig
import com.jupiter.filemanager.data.activation.ActivationManager
import com.jupiter.filemanager.data.activation.ReengagementScheduler
import com.jupiter.filemanager.data.preferences.AppStateDataStore
import com.jupiter.filemanager.feature.whatsnew.WhatsNewCatalog
import kotlinx.coroutines.flow.first
```

Add to `init { }` (after the existing calls):

```kotlin
        evaluateActivation()
```

Add these methods to the class body:

```kotlin
    /**
     * On first dashboard composition each launch: seed/evaluate the What's-New
     * gate, refresh the last-active timestamp, and ensure the re-engagement job
     * matches the persisted opt-in. Never throws; failures leave the UI untouched.
     */
    private fun evaluateActivation() {
        viewModelScope.launch {
            val versionCode = BuildConfig.VERSION_CODE
            activationManager.seedWhatsNewIfFresh(versionCode)
            activationManager.touchSession(System.currentTimeMillis())

            if (activationManager.shouldShowWhatsNew(versionCode)) {
                _uiState.update {
                    it.copy(
                        showWhatsNew = true,
                        whatsNewHighlights = WhatsNewCatalog.current,
                    )
                }
            }

            // Keep WorkManager in sync with the persisted opt-in.
            if (appState.reengageOptIn.first()) {
                reengagementScheduler.schedule()
            } else {
                reengagementScheduler.cancel()
            }
        }
    }

    /** Called by the UI once the What's-New sheet is dismissed. */
    fun onWhatsNewDismissed() {
        viewModelScope.launch {
            activationManager.markWhatsNewShown(BuildConfig.VERSION_CODE)
            _uiState.update { it.copy(showWhatsNew = false) }
        }
    }

    /**
     * Records a "positive moment" (e.g. returning to Home after a successful
     * cleanup) and, if the threshold is crossed, raises the one-shot review flag.
     */
    fun registerPositiveMoment() {
        viewModelScope.launch {
            if (activationManager.registerPositiveMomentAndCheck()) {
                _uiState.update { it.copy(launchReview = true) }
            }
        }
    }

    /** Clears the one-shot review flag after the Activity has launched the flow. */
    fun onReviewLaunched() {
        viewModelScope.launch {
            activationManager.markReviewRequested()
            _uiState.update { it.copy(launchReview = false) }
        }
    }
```

### 6.7 `feature/home/HomeScreen.kt` — host the sheet + review trigger

`HomeScreen` already collects `uiState` via `collectAsStateWithLifecycle()` (it imports it). Add, near the top of the composable body where `uiState` is available, the sheet host and the review side-effect.

Add imports:

```kotlin
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.jupiter.filemanager.data.activation.InAppReviewManager
import com.jupiter.filemanager.feature.whatsnew.WhatsNewSheet
import dagger.hilt.android.EntryPointAccessors
```

> **Obtaining `InAppReviewManager` in a composable:** the cleanest path is to pass it from the ViewModel. Add to `HomeViewModel` a public `val reviewManager: InAppReviewManager` injected in the constructor, exposed read-only, so the screen reuses the Hilt-provided singleton without an EntryPoint. Update the constructor accordingly (add `val inAppReviewManager: InAppReviewManager` parameter and remove the `EntryPointAccessors` import above). This is the recommended approach — it keeps DI in one place.

Inside `HomeScreen`, after `val uiState by viewModel.uiState.collectAsStateWithLifecycle()`:

```kotlin
    val activity = LocalActivity.current
    val reviewScope = rememberCoroutineScope()

    // Drive the in-app review flow exactly once when signalled.
    LaunchedEffect(uiState.launchReview) {
        if (uiState.launchReview && activity != null) {
            reviewScope.launch {
                viewModel.inAppReviewManager.launchReview(activity)
                viewModel.onReviewLaunched()
            }
        }
    }

    if (uiState.showWhatsNew) {
        WhatsNewSheet(
            highlights = uiState.whatsNewHighlights,
            onDismiss = { viewModel.onWhatsNewDismissed() },
        )
    }
```

> `LocalActivity` is provided by `androidx.activity.compose` (available via `activity-compose`, already a dependency). If your activity-compose version predates `LocalActivity`, fall back to `LocalContext.current as? Activity`.

### 6.8 Re-engagement opt-in toggle (Settings)

Add an opt-in switch to the existing `feature/settings/SettingsScreen.kt` / its ViewModel. The toggle:
1. Persists `appState.setReengageOptIn(value)`.
2. On enable (API 33+), requests `POST_NOTIFICATIONS` runtime permission via an `ActivityResultContracts.RequestPermission()` launcher hosted in the settings screen; if denied, revert the toggle and persist `false`.
3. On enable+granted, calls `reengagementScheduler.schedule()`. On disable, `reengagementScheduler.cancel()`.

Minimal ViewModel additions (inject `AppStateDataStore` + `ReengagementScheduler`):

```kotlin
    val reengageOptIn: StateFlow<Boolean> =
        appState.reengageOptIn.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setReengageOptIn(enabled: Boolean) {
        viewModelScope.launch {
            appState.setReengageOptIn(enabled)
            if (enabled) reengagementScheduler.schedule() else reengagementScheduler.cancel()
        }
    }
```

Compose toggle (place within the settings list; uses the existing screen's permission launcher):

```kotlin
val context = LocalContext.current
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission(),
) { granted ->
    if (granted) viewModel.setReengageOptIn(true) else viewModel.setReengageOptIn(false)
}

ListItem(
    headlineContent = { Text("Storage reminders") },
    supportingContent = { Text("Occasional nudge when space can be reclaimed.") },
    trailingContent = {
        Switch(
            checked = reengageOptIn,
            onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context, Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setReengageOptIn(enabled)
                }
            },
        )
    },
)
```

### 6.9 Triggering positive moments

Call `homeViewModel.registerPositiveMoment()` when the user returns to Home after a genuinely successful task. The lowest-risk hook: in `CleanupViewModel`/`CleanupScreen`, when a delete batch succeeds, set a result flag; `MainScreen`/Home observes it. Simplest concrete wiring: in `HomeScreen`, call `viewModel.registerPositiveMoment()` from a `LaunchedEffect(Unit)` **only** after the 3rd app session — but since session counting already feeds the threshold, the cleanest approach is: have `CleanupScreen` accept an `onCleanupSucceeded: () -> Unit` lambda wired in `JupiterNavHost` to a shared callback that increments via a small `ActivationManager`-backed call. If you prefer zero navigation changes, increment a positive moment from `HomeViewModel.init` once per launch (session-based), which alone reaches the threshold by the 3rd launch. Choose the session-based approach to guarantee no regression:

```kotlin
    // inside evaluateActivation(), after touchSession:
    if (activationManager.registerPositiveMomentAndCheck()) {
        _uiState.update { it.copy(launchReview = true) }
    }
```

This makes the review prompt fire on the 3rd launch — a well-established "satisfied user" heuristic — with zero new navigation surface.

### 6.10 Navigation / Destinations

**No changes** to `Destinations.kt` or `JupiterNavHost.kt` are required: What's-New is a sheet hosted in `HomeScreen`, and review/reengagement are ViewModel-driven. This is intentional to minimise regression surface. (If a future initiative wants a routed What's-New, add `data object WhatsNew : Destination("whats_new")` and a `composable` block — but not now.)

---

## 7. Phase 4 — Configuration

### 7.1 Keys / env

None. No API keys, no `local.properties` entries, no BuildConfig secrets. Version-bump detection uses the build's own `BuildConfig.VERSION_CODE`.

### 7.2 External service setup

- **Google Play In-App Review:** No console setup beyond having the app published (internal/closed track is enough to test live). Docs: https://developer.android.com/guide/playcore/in-app-review and https://developer.android.com/guide/playcore/in-app-review/kotlin-java . The library `com.google.android.play:review-ktx:2.0.2` is the only requirement.
- **WorkManager:** No external setup; uses the existing `HiltWorkerFactory` from `JupiterApp`.

### 7.3 ProGuard / R8 (`app/proguard-rules.pro`)

Release builds set `isMinifyEnabled = true`. Add keep rules so Play Core review and the Hilt worker survive shrinking:

```proguard
# Play In-App Review (Play Core)
-keep class com.google.android.play.core.review.** { *; }
-dontwarn com.google.android.play.core.**

# Re-engagement worker is instantiated reflectively by WorkManager/Hilt.
-keep class com.jupiter.filemanager.data.activation.ReengagementWorker { *; }
```

(Hilt and WorkManager generally ship consumer rules, but the explicit worker keep is cheap insurance.)

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. **Fresh install funnel:** Wipe app data → launch. Splash → Onboarding. Swipe through all 4 pages, tap "Get Started". Verify you reach Permission → Main. (DataStore now has `onboarding_furthest_step = 3`, `onboarding_outcome = completed`.)
2. **Skip funnel:** Wipe data → launch → on page 1 tap "Skip". Verify routing continues normally and `onboarding_outcome = skipped`, `onboarding_furthest_step = 0`.
3. **What's-New does NOT show on fresh install:** After step 1, you should land on Home with **no** sheet (fresh install seeded the shown-version).
4. **What's-New shows on bump:** Bump `versionCode` to `2` in `app/build.gradle.kts`, rebuild & install over the top (do not wipe data). Launch → reach Home → the What's-New sheet appears once. Tap "Got it". Relaunch → sheet does **not** reappear.
5. **Review prompt (debug = no-op):** Launch the app three times. On the 3rd launch, `launchReview` is raised and `InAppReviewManager.launchReview` runs. In debug/sideload it silently no-ops (expected); verify **no crash** and `review_requested = true` afterward (relaunch does not retrigger).
6. **Re-engagement opt-in:** Settings → enable "Storage reminders". On API 33+ grant the notification permission. Verify a unique periodic work `reengagement_periodic` is enqueued (`adb shell dumpsys jobscheduler | grep jupiter`). Disable → verify work cancelled.
7. **Re-engagement fire (forced):** Temporarily lower `INITIAL_DELAY_DAYS`/`REPEAT_INTERVAL_DAYS` or use `adb shell cmd jobscheduler run -f com.jupiter.filemanager.debug <jobId>` to force-run; verify the notification posts with a "X GB reclaimable" body when reclaimable > 200 MB, generic copy otherwise.
8. **No-regression sweep:** Confirm Onboarding pager, Splash routing, Home dashboard (volumes/quick-access/recents/bookmarks), Cleanup, Vault, Settings all still work exactly as before.

### 8.2 Recommended unit tests (`app/src/test/java/...`)

- `ActivationManagerTest` (fake `AppStateDataStore`-backed via a real in-memory `StorageAnalyticsRepository` fake):
  - `shouldShowWhatsNew` returns false on fresh install (lastShown == 0), false before onboarding complete, true when `versionCode > lastShown > 0`.
  - `registerPositiveMomentAndCheck` returns true only on the 3rd call and never after `markReviewRequested`.
  - `estimateReclaimableBytes` sums only cleanable categories; returns `Failure` propagated from a failing overview.
- `WhatsNewCatalogTest`: `current` is non-empty and every highlight has non-blank title/description.
- `ReengagementSchedulerTest` (Robolectric or `WorkManagerTestInitHelper`): `schedule()` enqueues unique periodic work; `cancel()` removes it; double `schedule()` keeps a single chain (KEEP policy).

### 8.3 Instrumented test (optional, `androidTest`)

- `OnboardingFunnelInstrumentedTest`: launch onboarding, swipe to page 2, assert `appStateDataStore` records `furthest_step >= 2`.

---

## 9. Error handling & edge cases (≥6)

1. **POST_NOTIFICATIONS revoked after opt-in (API 33+):** `ReengagementNotifier.postReengagement` wraps `notify` in `try/catch (SecurityException)` and silently skips — the worker still returns `Result.success()`, keeping the periodic chain alive. The Settings toggle re-requests permission on next enable.
2. **Play Review no-op / failure (debug, sideload, quota):** `InAppReviewManager.launchReview` resumes its continuation with `true` even when Play shows nothing, and returns `false` if `requestReviewFlow` failed. Either way `onReviewLaunched()` runs, sets `review_requested = true`, and clears the one-shot flag — never blocking the UI, never crashing.
3. **`storageOverview()` returns `Failure` (no storage permission yet):** `estimateReclaimableBytes` propagates the `Failure`; the worker maps it to a `null` label and posts the **generic** copy ("Tap to review files you can clean up"). No crash, no misleading figure.
4. **Reclaimable below threshold:** When the estimate is < 200 MB, the worker passes `null` to the notifier → generic copy. Avoids the embarrassing "0 B reclaimable" headline.
5. **DataStore read I/O error:** `AppStateDataStore.safe()` already catches `IOException` and emits empty preferences, so every new flag falls back to its default (`false`/`0`/`-1`). No new failure path introduced.
6. **Version downgrade / reinstall over data:** `shouldShowWhatsNew` requires `versionCode > lastShown`; a downgrade (`versionCode <= lastShown`) never shows the sheet. A reinstall that wipes data is treated as fresh (seeded, no retroactive sheet).
7. **Worker constructed before Hilt graph ready / double-schedule:** `ReengagementScheduler` uses `enqueueUniquePeriodicWork(..., KEEP, ...)`, so repeated `schedule()` calls never stack. The worker re-reads `reengageOptIn` at run time and no-ops (still `success`) if the user opted out after scheduling.
8. **What's-New shown but user kills app before dismiss:** The shown-version is persisted only in `onWhatsNewDismissed`. If killed first, the sheet re-shows next launch (acceptable — it is shown at most once per version once dismissed). To guarantee at-most-once even without dismiss, optionally call `markWhatsNewShown` when the sheet first appears; current design intentionally favours "user actually saw it".

---

## 10. Integration with other initiatives

- **#1 Pro monetization (funnel):** Activation/retention feeds the top of the Pro funnel. The positive-moment counter and What's-New are natural surfaces to *cross-sell Pro* later (e.g. a "Go Pro" highlight in `WhatsNewCatalog`). No code coupling now; keep `WhatsNewCatalog` editable.
- **#4 AI Pro suite:** A successful AI action is an excellent "positive moment" — once #4 lands, call `homeViewModel.registerPositiveMoment()` (or a shared `ActivationManager` hook) after a successful AI task instead of the session-based heuristic.
- **#5 Widgets/Shortcuts/Tiles:** A re-engagement notification and a home-screen widget both pull from `StorageAnalyticsRepository.storageOverview()`; share the reclaimable estimate logic in `ActivationManager.estimateReclaimableBytes()` to avoid divergence.
- **Future analytics initiative:** Funnel events are persisted in `AppStateDataStore` in a forwarding-friendly shape (`onboarding_furthest_step`, `onboarding_outcome`). When an analytics SDK is added, forward these from `ActivationManager.recordFunnel` — a one-line addition, no schema change.
- **Dependencies:** This initiative depends on nothing else and blocks nothing else. It is purely additive and can ship independently.

---

## 11. Rollback plan

Because the initiative is additive, rollback is deletion + a few reverts:

1. **Revert edits** in: `AppStateDataStore.kt` (remove new keys/accessors), `OnboardingViewModel.kt`, `OnboardingScreen.kt` (restore `finish()`), `HomeViewModel.kt`, `HomeUiState.kt`, `HomeScreen.kt`, and the Settings screen/ViewModel toggle. Git: `git checkout <pre-initiative-sha> -- <those files>`.
2. **Delete new packages:** `feature/whatsnew/`, `data/activation/`, `di/ActivationModule.kt`.
3. **Remove Gradle dep:** delete the `play-review-ktx` line from `app/build.gradle.kts` and the catalog entries in `libs.versions.toml`.
4. **Remove resources:** the `reengage_*` and `whatsnew_*` strings; the ProGuard rules block.
5. **Cancel any scheduled work** on the next launch: the removed `evaluateActivation` no longer schedules; existing enqueued `reengagement_periodic` work will simply fail to find its worker class and WorkManager will drop it. Optionally ship a one-time migration that calls `WorkManager.getInstance(context).cancelUniqueWork("reengagement_periodic")` in `JupiterApp.onCreate` for one release.
6. No DataStore migration needed — orphaned keys are harmless and ignored.

Partial rollback is possible per-mechanism: e.g. disable only re-engagement by hiding the Settings toggle and short-circuiting `schedule()`; the rest keeps working.

---

## 12. Definition of done

- [ ] `play-review-ktx:2.0.2` added to `libs.versions.toml` + `app/build.gradle.kts`; project syncs.
- [ ] `AppStateDataStore` extended with all 7 new keys/accessors; **existing `onboardingCompleted` API unchanged**.
- [ ] New `data/activation/*` files (`ActivationManager`, `InAppReviewManager`, `ReengagementNotifier`, `ReengagementWorker`, `ReengagementScheduler`, `OnboardingFunnelEvent`) and `di/ActivationModule.kt` compile and are Hilt-wired.
- [ ] `feature/whatsnew/WhatsNewSheet.kt` + `WhatsNewContent.kt` render a Material3 `ModalBottomSheet`.
- [ ] Onboarding emits funnel events (page-viewed, skip vs complete) verified via DataStore.
- [ ] What's-New shows **once per version bump**, never on fresh install, never on downgrade; dismissal persists.
- [ ] In-app review flow fires at the positive-moment threshold, is one-shot (`review_requested`), and **no-ops without crashing** in debug.
- [ ] Re-engagement: Settings opt-in requests `POST_NOTIFICATIONS` on API 33+, schedules a unique periodic worker, cancels on opt-out; worker posts "X GB reclaimable" (or generic) and never crashes.
- [ ] ProGuard keep rules added; **release `assembleRelease` succeeds with minify on**.
- [ ] **No regression: Onboarding pager + Splash routing still work** (fresh install reaches Permission/Main exactly as before).
- [ ] **No regression: Home dashboard (volumes, quick-access, recents, bookmarks) and Cleanup/Vault/Settings still work**; existing `AutomationWorker` WorkManager usage unaffected.
- [ ] **CI green: `./gradlew assembleDebug` and `./gradlew testDebugUnitTest` both pass.**
- [ ] Unit tests for `ActivationManager` (what's-new gating, review threshold, reclaimable sum) and `ReengagementScheduler` (enqueue/cancel/KEEP) added and passing.
- [ ] Manual smoke-test script (§8.1) executed end-to-end on an API 33+ and an API 26 device/emulator.
