# Initiative #5 — Home-screen Widgets, App Shortcuts & Quick-Settings Tile

## 1. Initiative header

**Title:** Home-screen Widgets, App Shortcuts & Quick-Settings Tile
**Estimated value:** **+€90k–€180k / year**

**Business case.** Jupiter today lives entirely inside its own task stack: the only way a user reaches any feature is to launch the app and tap through the shell. Every retention and DAU lever in a utility file manager comes from *surfaces outside the app* — the home screen, the long-press app menu, and the notification shade. This initiative adds three Android-native surfaces with no new server dependency: a **Glance home-screen widget** showing a live storage gauge plus one-tap entry points (Browse / Search / Cleanup), **static + dynamic app shortcuts** (long-press the launcher icon → Search, Cleanup, Vault, plus recently-visited folders), and a **Quick-Settings tile** for one-tap storage cleanup from the shade. These surfaces are proven DAU drivers: a widget on the home screen is seen dozens of times per day and re-engages lapsed users without a notification, app shortcuts shorten the path to monetizable features (Cleanup, Vault), and a QS tile creates a habitual "free up space" gesture. The work is **purely additive** — new packages, new manifest entries, one Gradle dependency — and touches **zero** existing screen, ViewModel, repository or navigation behavior, so there is no regression surface for current users.

---

## 2. Codebase context

The real Jupiter codebase lives at `/home/user/Jupiter/app/src/main/java/com/jupiter/filemanager`. Package root: `com.jupiter.filemanager`. The following real files are relevant and were read to author this prompt.

### Current relevant file tree (real, verified)

```
app/
  build.gradle.kts                                  # app module deps (no glance yet)
  src/main/
    AndroidManifest.xml                             # 1 activity, 1 provider; no widget/tile/shortcuts
    res/
      values/strings.xml                            # has app_name, label_cleanup, label_search, label_vault…
      values/themes.xml  values/colors.xml
      xml/{file_paths,backup_rules,data_extraction_rules}.xml
      drawable/   mipmap-anydpi-v26/
    java/com/jupiter/filemanager/
      JupiterApp.kt                                 # @HiltAndroidApp, Configuration.Provider (WorkManager)
      MainActivity.kt                               # @AndroidEntryPoint, single-activity, JupiterNavHost
      MainViewModel.kt                              # themeMode flow
      core/result/
        AppResult.kt                                # sealed AppResult<T> { Success, Failure }
        AppError.kt                                 # sealed AppError { PermissionDenied, NotFound, Io, … }
      di/
        CoroutineModule.kt                          # @IoDispatcher / @DefaultDispatcher / @MainDispatcher
        RepositoryModule.kt  FeatureRepositoryModule.kt  RemoteModule.kt  AiModule.kt
      domain/
        model/
          StorageVolumeInfo.kt                      # id,label,rootPath,total,available,usedBytes,usedFraction
          StorageAnalysis.kt                        # StorageOverview, CategoryUsage, StorageCategory, DuplicateGroup
        repository/
          StorageAnalyticsRepository.kt             # suspend storageOverview(): AppResult<StorageOverview>
          FileRepository.kt                         # fun rootDirectory(): String
      data/
        storage/
          StorageVolumeProvider.kt                  # @Singleton; primaryRoot(), volumes(): List<StorageVolumeInfo>
          StorageAnalyticsRepositoryImpl.kt
      feature/
        cleanup/CleanupViewModel.kt  CleanupScreen.kt   # delete-selected cleanup flow
        splash/SplashScreen.kt SplashViewModel.kt       # decides Onboarding/Permission/Main
        …
      ui/navigation/
        Destinations.kt                             # sealed Destination(route): Browser, Search, Cleanup, Vault…
        JupiterNavHost.kt                           # NavHost wiring all Destinations
```

### What exists vs missing vs needs change

| Item | Status | Action |
|---|---|---|
| `StorageVolumeProvider.volumes()` / `primaryRoot()` | **Exists** (`@Singleton`, injectable) | **Reuse** — widget gauge + tile read it. No change. |
| `Destination.{Browser,Search,Cleanup,Vault}` | **Exists** | **Reuse** as deep-link targets. No change. |
| `MainActivity` single-activity host | **Exists** | **Change (additive only):** add `intent` deep-link handling that navigates after the nav graph is ready. The default branch keeps today's behavior. |
| `AppResult` / `AppError` / `@IoDispatcher` | **Exists** | **Reuse** in the new widget data layer. No change. |
| Glance dependency | **Missing** | **Add** `androidx.glance:glance-appwidget`. |
| `app/widget/` (Glance AppWidget + receiver) | **Missing** | **Create.** |
| `app/shortcuts/` (static `shortcuts.xml` + dynamic publisher) | **Missing** | **Create.** |
| `app/tile/` (QS `TileService`) | **Missing** | **Create.** |
| `di/SurfaceModule.kt` (bind new repo) | **Missing** | **Create.** |
| Manifest entries for widget receiver / tile service / shortcuts meta-data | **Missing** | **Add.** |

> Naming note: the package segment requested is `widget` / `shortcuts` / `tile` under `feature/` to match the existing `feature/*` convention. Final package paths used below: `com.jupiter.filemanager.feature.widget`, `…feature.shortcuts`, `…feature.tile`. These are brand-new packages and collide with nothing.

---

## 3. Pre-conditions

**Gradle dependency to add (exact coordinates):**

- `androidx.glance:glance-appwidget:1.1.1` — Glance AppWidget runtime (Compose-style widget UI, ships its own Material/Color support). It transitively pulls `androidx.glance:glance:1.1.1`.

No other library is required. No network client, no Room table, no DataStore key, no API key, no Play Console configuration, no signing change. Everything reads local storage stats already exposed by `StorageVolumeProvider`.

**Platform prerequisites (already satisfied by the module):**

- `minSdk = 26`, `targetSdk = 35`, `compileSdk = 35` — Glance 1.1.x requires `minSdk >= 21`; QS `TileService` requires API 24; dynamic shortcuts require API 25; **all satisfied** by `minSdk 26`.
- `buildFeatures { compose = true }` is already on — Glance compiles independently of app Compose but coexists fine.
- Kotlin `2.0.21`, Compose compiler plugin already applied. **Do not** add `glance` composables to the app's own Compose tree; Glance has its own composition runtime.

**Permissions:** none new. The widget shows capacity from `StatFs` on app-private dirs (no permission needed) and from the primary root which is already gated by the existing storage-permission flow. If storage permission has not been granted, the widget degrades gracefully (see §9).

---

## 4. Phase 1 — Gradle + Manifest + resources

### 4.1 `gradle/libs.versions.toml`

Add the version and library entries (append to the existing `[versions]` and `[libraries]` tables — do not reorder existing lines).

```toml
# [versions] — append
glance = "1.1.1"

# [libraries] — append
androidx-glance-appwidget = { group = "androidx.glance", name = "glance-appwidget", version.ref = "glance" }
```

### 4.2 `app/build.gradle.kts`

Add one line inside the existing `dependencies { … }` block, in the "Compose" group:

```kotlin
    // Home-screen widget (Glance). Independent composition runtime from app Compose.
    implementation(libs.androidx.glance.appwidget)
```

No `kotlinOptions`, `buildFeatures`, or plugin changes are needed.

### 4.3 `AndroidManifest.xml`

Add the following **inside the existing `<application>` element**, after the `<provider>` block and before `</application>`. Nothing existing is modified.

```xml
        <!-- ============================================================
             Initiative #5 — out-of-app surfaces (additive)
             ============================================================ -->

        <!-- Home-screen Glance widget: storage gauge + quick actions. -->
        <receiver
            android:name=".feature.widget.JupiterGlanceReceiver"
            android:exported="false"
            android:label="@string/widget_title">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/jupiter_glance_widget_info" />
        </receiver>

        <!-- Quick-Settings tile: one-tap storage cleanup from the shade (API 24+). -->
        <service
            android:name=".feature.tile.CleanupTileService"
            android:exported="true"
            android:icon="@drawable/ic_qs_cleanup"
            android:label="@string/tile_cleanup_label"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
            <meta-data
                android:name="android.service.quicksettings.ACTIVE_TILE"
                android:value="false" />
        </service>
```

Also add the **static shortcuts meta-data inside the existing `<activity android:name=".MainActivity">`** element, after its `<intent-filter>` (additive — the launcher filter is untouched):

```xml
            <meta-data
                android:name="android.app.shortcuts"
                android:resource="@xml/shortcuts" />
```

### 4.4 New resource files

**`app/src/main/res/xml/jupiter_glance_widget_info.xml`** (AppWidget provider metadata):

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:targetCellWidth="3"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:initialLayout="@layout/glance_default_loading"
    android:description="@string/widget_description"
    android:previewImage="@drawable/ic_qs_cleanup"
    android:updatePeriodMillis="1800000" />
```

**`app/src/main/res/layout/glance_default_loading.xml`** (initial layout shown before Glance first composes — required by the platform):

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="?android:attr/colorBackground"
    android:padding="16dp">
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:text="@string/widget_loading"
        android:textColor="?android:attr/textColorPrimary" />
</FrameLayout>
```

**`app/src/main/res/xml/shortcuts.xml`** (static app shortcuts):

```xml
<?xml version="1.0" encoding="utf-8"?>
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">

    <shortcut
        android:shortcutId="search"
        android:enabled="true"
        android:icon="@drawable/ic_shortcut_search"
        android:shortcutShortLabel="@string/shortcut_search_short"
        android:shortcutLongLabel="@string/shortcut_search_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.jupiter.filemanager"
            android:targetClass="com.jupiter.filemanager.MainActivity">
            <extra android:name="jupiter.extra.DESTINATION" android:value="search" />
        </intent>
        <categories android:name="android.shortcut.conversation" />
    </shortcut>

    <shortcut
        android:shortcutId="cleanup"
        android:enabled="true"
        android:icon="@drawable/ic_shortcut_cleanup"
        android:shortcutShortLabel="@string/shortcut_cleanup_short"
        android:shortcutLongLabel="@string/shortcut_cleanup_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.jupiter.filemanager"
            android:targetClass="com.jupiter.filemanager.MainActivity">
            <extra android:name="jupiter.extra.DESTINATION" android:value="cleanup" />
        </intent>
        <categories android:name="android.shortcut.conversation" />
    </shortcut>

    <shortcut
        android:shortcutId="vault"
        android:enabled="true"
        android:icon="@drawable/ic_shortcut_vault"
        android:shortcutShortLabel="@string/shortcut_vault_short"
        android:shortcutLongLabel="@string/shortcut_vault_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="com.jupiter.filemanager"
            android:targetClass="com.jupiter.filemanager.MainActivity">
            <extra android:name="jupiter.extra.DESTINATION" android:value="vault" />
        </intent>
        <categories android:name="android.shortcut.conversation" />
    </shortcut>
</shortcuts>
```

**Vector drawables** — create four monochrome 24dp vectors. Use `?attr/colorControlNormal`-compatible tints; for shortcuts the system masks them. Exact files:

`app/src/main/res/drawable/ic_qs_cleanup.xml`:

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M6,19c0,1.1 0.9,2 2,2h8c1.1,0 2,-0.9 2,-2V7H6v12zM19,4h-3.5l-1,-1h-5l-1,1H5v2h14V4z"/>
</vector>
```

`ic_shortcut_search.xml`, `ic_shortcut_cleanup.xml`, `ic_shortcut_vault.xml` — analogous 24dp vectors (use any Material path data: a magnifier for search, the trash path above for cleanup, a shield/lock for vault). Keep `android:tint="?attr/colorControlNormal"`.

**`app/src/main/res/values/strings.xml`** — append inside `<resources>` (do not touch existing strings):

```xml
    <!-- Initiative #5 — widgets / shortcuts / tile -->
    <string name="widget_title">Jupiter Storage</string>
    <string name="widget_description">Storage gauge and quick actions for Jupiter.</string>
    <string name="widget_loading">Loading…</string>
    <string name="widget_used_format">%1$s of %2$s used</string>
    <string name="widget_action_browse">Browse</string>
    <string name="widget_action_search">Search</string>
    <string name="widget_action_cleanup">Clean up</string>
    <string name="widget_permission_needed">Grant storage access</string>
    <string name="tile_cleanup_label">Clean up storage</string>
    <string name="shortcut_search_short">Search</string>
    <string name="shortcut_search_long">Search files</string>
    <string name="shortcut_cleanup_short">Clean up</string>
    <string name="shortcut_cleanup_long">Free up space</string>
    <string name="shortcut_vault_short">Vault</string>
    <string name="shortcut_vault_long">Open secure vault</string>
```

---

## 5. Phase 2 — Data/domain layer

The widget and tile need a tiny, side-effect-free snapshot of storage usage. We expose it through a new repository that **only reuses** the existing `StorageVolumeProvider`. All blocking work runs on `@IoDispatcher`; errors are mapped to `AppError`/`AppResult`.

### 5.1 Domain model — `domain/model/StorageSnapshot.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.model

/**
 * A minimal, immutable snapshot of primary-volume usage for out-of-app surfaces
 * (home-screen widget, Quick-Settings tile). Deliberately tiny so it can be
 * computed cheaply and frequently without a full storage scan.
 *
 * @property label user-facing volume label (e.g. "Internal storage").
 * @property usedBytes bytes currently in use on the primary volume.
 * @property totalBytes total capacity of the primary volume.
 * @property rootPath absolute path of the primary volume root, used for deep-links.
 */
data class StorageSnapshot(
    val label: String,
    val usedBytes: Long,
    val totalBytes: Long,
    val rootPath: String,
) {
    /** Fraction used in [0f, 1f]; 0f when capacity is unknown. */
    val usedFraction: Float
        get() = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    val availableBytes: Long get() = (totalBytes - usedBytes).coerceAtLeast(0L)

    companion object {
        /** Neutral snapshot used when capacity cannot be read (no permission / no volume). */
        val UNKNOWN = StorageSnapshot(label = "Storage", usedBytes = 0L, totalBytes = 0L, rootPath = "/storage/emulated/0")
    }
}
```

### 5.2 Repository interface — `domain/repository/SurfaceStorageRepository.kt` (NEW)

```kotlin
package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.StorageSnapshot

/**
 * Read-only source of the lightweight [StorageSnapshot] consumed by the
 * home-screen widget and the Quick-Settings tile. Implementations must be safe
 * to call from any context (BroadcastReceiver/TileService) and never throw.
 */
interface SurfaceStorageRepository {

    /**
     * Returns a snapshot of the primary storage volume. On any failure (no
     * mounted volume, missing permission, inaccessible StatFs) returns
     * [AppResult.Failure] with a descriptive [com.jupiter.filemanager.core.result.AppError];
     * callers should fall back to [StorageSnapshot.UNKNOWN].
     */
    suspend fun primarySnapshot(): AppResult<StorageSnapshot>
}
```

### 5.3 Implementation — `data/storage/SurfaceStorageRepositoryImpl.kt` (NEW)

```kotlin
package com.jupiter.filemanager.data.storage

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.StorageSnapshot
import com.jupiter.filemanager.domain.repository.SurfaceStorageRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Computes a [StorageSnapshot] from the already-existing [StorageVolumeProvider].
 * All work is delegated to [StatFs] reads inside [StorageVolumeProvider.volumes],
 * which are cheap and require no permission for app-private resolution. Wrapped on
 * [IoDispatcher] and fully exception-safe so widget/tile callers never crash.
 */
@Singleton
class SurfaceStorageRepositoryImpl @Inject constructor(
    private val volumeProvider: StorageVolumeProvider,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SurfaceStorageRepository {

    override suspend fun primarySnapshot(): AppResult<StorageSnapshot> = withContext(ioDispatcher) {
        runCatching {
            val volumes = volumeProvider.volumes()
            val primary = volumes.firstOrNull { it.isPrimary }
                ?: volumes.firstOrNull()
                ?: return@runCatching null
            StorageSnapshot(
                label = primary.label,
                usedBytes = primary.usedBytes,
                totalBytes = primary.totalBytes,
                rootPath = primary.rootPath,
            )
        }.fold(
            onSuccess = { snapshot ->
                if (snapshot == null) {
                    AppResult.Failure(AppError.NotFound("primary storage volume"))
                } else {
                    AppResult.Success(snapshot)
                }
            },
            onFailure = { t ->
                AppResult.Failure(AppError.Io(detail = t.message ?: "Failed to read storage", cause = t))
            },
        )
    }
}
```

### 5.4 Hilt module — `di/SurfaceModule.kt` (NEW)

```kotlin
package com.jupiter.filemanager.di

import com.jupiter.filemanager.data.storage.SurfaceStorageRepositoryImpl
import com.jupiter.filemanager.domain.repository.SurfaceStorageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the out-of-app surface data layer (home-screen widget + QS tile).
 * Additive: introduces no overrides of existing bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SurfaceModule {

    @Binds
    @Singleton
    abstract fun bindSurfaceStorageRepository(
        impl: SurfaceStorageRepositoryImpl,
    ): SurfaceStorageRepository
}
```

### 5.5 Hilt entry point for non-injectable Android components

`GlanceAppWidget`, `BroadcastReceiver`, and `TileService` cannot use constructor injection cleanly across all Glance/Hilt versions, so we resolve the repository through a Hilt `EntryPoint`. `data/storage/SurfaceEntryPoint.kt` (NEW):

```kotlin
package com.jupiter.filemanager.data.storage

import com.jupiter.filemanager.domain.repository.SurfaceStorageRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt entry point exposing the surface repository to framework components
 * (Glance widget, QS tile) that are not Hilt-managed and cannot use @Inject.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SurfaceEntryPoint {
    fun surfaceStorageRepository(): SurfaceStorageRepository
}
```

---

## 6. Phase 3 — Presentation (Glance widget, shortcuts publisher, tile, deep-link wiring)

> Note: the home-screen "presentation" here is a Glance composition, not the app's Material3 Compose tree, and there is **no `@HiltViewModel`/navigation route for the widget itself** — the widget is a separate surface. The "navigation wiring" deliverable for this initiative is the **deep-link routing into the existing `JupiterNavHost`** (§6.4), which is the part that touches the app's nav graph.

### 6.1 Deep-link contract (shared constant) — `feature/shortcuts/SurfaceDeepLink.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.shortcuts

import android.content.Context
import android.content.Intent
import com.jupiter.filemanager.MainActivity

/**
 * Single source of truth for how out-of-app surfaces (widget, shortcuts, tile)
 * ask [MainActivity] to navigate. Each surface builds an Intent carrying
 * [EXTRA_DESTINATION] (and optional [EXTRA_PATH]); MainActivity reads these and
 * routes through the existing JupiterNavHost. Values mirror Destination.route.
 */
object SurfaceDeepLink {
    const val EXTRA_DESTINATION = "jupiter.extra.DESTINATION"
    const val EXTRA_PATH = "jupiter.extra.PATH"

    const val DEST_BROWSER = "browser"
    const val DEST_SEARCH = "search"
    const val DEST_CLEANUP = "cleanup"
    const val DEST_VAULT = "vault"

    /** Builds an intent that launches MainActivity at [destination] (optionally a [path]). */
    fun intent(context: Context, destination: String, path: String? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_DESTINATION, destination)
            if (path != null) putExtra(EXTRA_PATH, path)
        }
}
```

### 6.2 Glance widget UI + receiver — `feature/widget/JupiterGlanceWidget.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.storage.SurfaceEntryPoint
import com.jupiter.filemanager.domain.model.StorageSnapshot
import com.jupiter.filemanager.feature.shortcuts.SurfaceDeepLink
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale

/**
 * Home-screen Glance widget: a live storage gauge plus three quick actions
 * (Browse / Search / Clean up) that deep-link into the app. State is loaded once
 * per [provideContent] pass via the [SurfaceEntryPoint]; the platform refreshes it
 * on the manifest's updatePeriodMillis and whenever [JupiterGlanceReceiver]
 * receives an explicit update.
 */
class JupiterGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = loadSnapshot(context)
        provideContent {
            GlanceTheme {
                WidgetBody(context, snapshot)
            }
        }
    }

    private suspend fun loadSnapshot(context: Context): WidgetState {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, SurfaceEntryPoint::class.java)
            .surfaceStorageRepository()
        return when (val result = repo.primarySnapshot()) {
            is AppResult.Success -> WidgetState.Ready(result.data)
            is AppResult.Failure -> WidgetState.Unavailable
        }
    }
}

/** Immutable Glance render state — mirrors the UiState pattern used in app ViewModels. */
sealed interface WidgetState {
    data class Ready(val snapshot: StorageSnapshot) : WidgetState
    data object Unavailable : WidgetState
}

@Composable
private fun WidgetBody(context: Context, state: WidgetState) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(20.dp)
            .padding(16.dp),
    ) {
        Text(
            text = "Jupiter",
            style = TextStyle(fontWeight = FontWeight.Bold, color = GlanceTheme.colors.onSurface),
        )
        Spacer(GlanceModifier.height(8.dp))

        when (state) {
            is WidgetState.Ready -> Gauge(state.snapshot)
            WidgetState.Unavailable -> Text(
                text = "Tap to open Jupiter",
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                modifier = GlanceModifier.clickable(
                    actionStartActivity(SurfaceDeepLink.intent(context, SurfaceDeepLink.DEST_BROWSER)),
                ),
            )
        }

        Spacer(GlanceModifier.height(12.dp))
        ActionRow(context)
    }
}

@Composable
private fun Gauge(snapshot: StorageSnapshot) {
    LinearProgressIndicator(
        progress = snapshot.usedFraction,
        modifier = GlanceModifier.fillMaxWidth(),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
    Spacer(GlanceModifier.height(6.dp))
    Text(
        text = String.format(
            Locale.getDefault(),
            "%s of %s used",
            humanReadable(snapshot.usedBytes),
            humanReadable(snapshot.totalBytes),
        ),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
    )
}

@Composable
private fun ActionRow(context: Context) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetButton("Browse", context, SurfaceDeepLink.DEST_BROWSER)
        Spacer(GlanceModifier.width(8.dp))
        WidgetButton("Search", context, SurfaceDeepLink.DEST_SEARCH)
        Spacer(GlanceModifier.width(8.dp))
        WidgetButton("Clean up", context, SurfaceDeepLink.DEST_CLEANUP)
    }
}

@Composable
private fun WidgetButton(label: String, context: Context, destination: String) {
    Text(
        text = label,
        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier
            .background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(actionStartActivity(SurfaceDeepLink.intent(context, destination))),
    )
}

private fun humanReadable(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unit])
}

/**
 * AppWidget host receiver registered in the manifest. Delegates rendering to
 * [JupiterGlanceWidget]. No Hilt annotation needed — the widget resolves its
 * dependencies via [SurfaceEntryPoint] at render time.
 */
class JupiterGlanceReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = JupiterGlanceWidget()
}
```

> Import note: add `import androidx.glance.layout.width` (used by `GlanceModifier.width`). If a specific Glance 1.1.1 symbol name differs (`backgroundColor` vs `trackColor` on `LinearProgressIndicator`), use the signature the resolved artifact exposes — the agent should let the compiler confirm and adjust the parameter name only, keeping the structure identical.

### 6.3 Dynamic shortcuts publisher — `feature/shortcuts/JupiterShortcutManager.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.shortcuts

import android.content.Context
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import com.jupiter.filemanager.R
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.StorageSnapshot
import com.jupiter.filemanager.domain.repository.SurfaceStorageRepository
import com.jupiter.filemanager.core.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Publishes dynamic app shortcuts: a "Free up space" entry annotated with the
 * current used-fraction. Static shortcuts (Search/Cleanup/Vault) come from
 * res/xml/shortcuts.xml; this manager adds one dynamic, data-driven shortcut.
 *
 * Safe to call from any lifecycle point; all ShortcutManager calls are guarded.
 */
@Singleton
class JupiterShortcutManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageRepository: SurfaceStorageRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun refreshDynamicShortcuts() = withContext(ioDispatcher) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return@withContext
        val snapshot = (storageRepository.primarySnapshot() as? AppResult.Success)?.data
            ?: StorageSnapshot.UNKNOWN

        val percent = (snapshot.usedFraction * 100f).toInt().coerceIn(0, 100)
        val cleanup = ShortcutInfo.Builder(context, "dynamic_cleanup")
            .setShortLabel("Free up space")
            .setLongLabel("Free up space ($percent% used)")
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut_cleanup))
            .setIntent(SurfaceDeepLink.intent(context, SurfaceDeepLink.DEST_CLEANUP))
            .build()

        runCatching { manager.dynamicShortcuts = listOf(cleanup) }
    }
}
```

### 6.4 Deep-link handling in `MainActivity.kt` (CHANGE — additive only)

Full replacement of `MainActivity.kt`. The only additions vs the real file are: a `pendingDeepLink` state, reading the intent in `onCreate`/`onNewIntent`, and a `LaunchedEffect` that navigates once the nav graph exists. The default path (no extra) is byte-for-byte the current behavior.

```kotlin
package com.jupiter.filemanager

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.feature.shortcuts.SurfaceDeepLink
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.navigation.JupiterNavHost
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for Jupiter. In addition to the splash → main flow, it
 * accepts out-of-app deep links (home-screen widget, app shortcuts, QS tile)
 * carried as [SurfaceDeepLink.EXTRA_DESTINATION] on the launch intent and routes
 * to the corresponding [Destination] once the nav graph is composed. Absent the
 * extra, behavior is identical to before.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkDestination = intent?.getStringExtra(SurfaceDeepLink.EXTRA_DESTINATION)
        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode: ThemeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()

            JupiterTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    JupiterNavHost(
                        navController = navController,
                        startDestination = Destination.Splash.route,
                    )

                    val pending = deepLinkDestination
                    LaunchedEffect(pending) {
                        if (pending != null) {
                            val route = routeFor(pending)
                            if (route != null) {
                                navController.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                            deepLinkDestination = null
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkDestination = intent.getStringExtra(SurfaceDeepLink.EXTRA_DESTINATION)
    }

    private fun routeFor(destination: String): String? = when (destination) {
        SurfaceDeepLink.DEST_SEARCH -> Destination.Search.route
        SurfaceDeepLink.DEST_CLEANUP -> Destination.Cleanup.route
        SurfaceDeepLink.DEST_VAULT -> Destination.Vault.route
        SurfaceDeepLink.DEST_BROWSER -> Destination.Browser.create(
            intent?.getStringExtra(SurfaceDeepLink.EXTRA_PATH) ?: "",
        ).takeIf { (intent?.getStringExtra(SurfaceDeepLink.EXTRA_PATH) ?: "").isNotEmpty() }
            ?: Destination.Main.route
        else -> null
    }
}
```

> Why this is regression-safe: `Destination.Search/Cleanup/Vault` are static routes already registered in `JupiterNavHost`. When the deep link points to one of them, `navController.navigate(route)` pushes that screen on top of the splash→main back stack once the graph exists. If the splash flow has not yet resolved to Main, the navigate runs against the splash destination and Navigation Compose queues onto the live graph; `launchSingleTop` prevents duplicates. `DEST_BROWSER` with no path routes to `Main` (today's default landing). No existing call site passes the extra, so existing launches are unchanged.

### 6.5 No new `Destination` entries are required

All four targets (`Browser`, `Search`, `Cleanup`, `Vault`) already exist in `Destinations.kt` and are wired in `JupiterNavHost`. **Do not** add or rename destinations. This keeps the nav graph diff at zero.

---

## 7. Phase 4 — Configuration

### 7.1 Quick-Settings tile service — `feature/tile/CleanupTileService.kt` (NEW)

```kotlin
package com.jupiter.filemanager.feature.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.storage.SurfaceEntryPoint
import com.jupiter.filemanager.domain.model.StorageSnapshot
import com.jupiter.filemanager.feature.shortcuts.SurfaceDeepLink
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Quick-Settings tile: shows current used-% as its subtitle and, on tap, launches
 * the app directly into the Cleanup screen via [SurfaceDeepLink]. Reads storage
 * through [SurfaceEntryPoint]; never throws. API 24+ (guaranteed by minSdk 26).
 */
class CleanupTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        val intent = SurfaceDeepLink.intent(applicationContext, SurfaceDeepLink.DEST_CLEANUP)
        // startActivityAndCollapse requires a launch; intent already carries NEW_TASK.
        startActivityAndCollapse(
            android.app.PendingIntent.getActivity(
                this, 0, intent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun refreshTile() {
        val tile = qsTile ?: return
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, SurfaceEntryPoint::class.java)
            .surfaceStorageRepository()
        val snapshot = when (val r = repo.primarySnapshot()) {
            is AppResult.Success -> r.data
            is AppResult.Failure -> StorageSnapshot.UNKNOWN
        }
        val percent = (snapshot.usedFraction * 100f).toInt().coerceIn(0, 100)
        tile.label = "Clean up storage"
        tile.subtitle = String.format(Locale.getDefault(), "%d%% used", percent)
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
```

> `tile.subtitle` requires API 29; on 26–28 the assignment is a harmless no-op via try/catch is **not** needed because the setter exists since API 29 and is annotated — wrap it: `runCatching { tile.subtitle = … }`. Apply that guard if the agent targets devices < 29 strictly; on API 29+ it shows. Keep `tile.label`/`tile.state`/`updateTile()` unconditional.

### 7.2 Optional: refresh dynamic shortcuts on launch

To keep the dynamic "Free up space (X% used)" label fresh, call `JupiterShortcutManager.refreshDynamicShortcuts()` from `MainActivity.onResume` in a lifecycle-scoped coroutine. This is optional polish; static shortcuts work with zero code. If added, inject via a small `@AndroidEntryPoint`-scoped helper or a `@HiltViewModel`; do not block the main thread (the manager already uses `@IoDispatcher`).

### 7.3 External service setup

**None.** No OAuth, no API key, no remote endpoint, no Play Console widget registration (Android publishes widget/shortcut/tile capability from the manifest automatically). The widget appears in the launcher's widget picker; the tile appears in the user's "Edit tiles" tray in the shade; shortcuts appear on launcher long-press.

### 7.4 ProGuard / R8 (`app/proguard-rules.pro`)

The release build has `isMinifyEnabled = true`. Glance and the framework components must survive shrinking. Append:

```pro
# Glance widget — receiver + widget referenced only from manifest/reflection.
-keep class com.jupiter.filemanager.feature.widget.JupiterGlanceReceiver { *; }
-keep class com.jupiter.filemanager.feature.widget.JupiterGlanceWidget { *; }
-keep class * extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Quick-Settings tile service (manifest-referenced).
-keep class com.jupiter.filemanager.feature.tile.CleanupTileService { *; }

# Glance/runtime generated state classes.
-keep class androidx.glance.** { *; }
-dontwarn androidx.glance.**
```

---

## 8. Phase 5 — Testing

### 8.1 Manual smoke-test script

1. `./gradlew :app:assembleDebug` → build succeeds.
2. Install on an API 26 and an API 34 device/emulator.
3. **Widget:** long-press home screen → Widgets → Jupiter → drop "Jupiter Storage". Verify the gauge shows a non-zero used fraction and a `"X of Y used"` line. Resize the widget — it reflows.
4. Tap **Browse** / **Search** / **Clean up** chips → app opens directly on the matching screen (Main, Search, Cleanup respectively).
5. **Shortcuts:** long-press the Jupiter launcher icon → see Search / Clean up / Vault static shortcuts + a dynamic "Free up space (NN% used)". Tap each → lands on the right screen.
6. **Tile:** pull down the shade → Edit tiles → drag "Clean up storage" in. Verify subtitle shows `NN% used` (API 29+). Tap the tile → shade collapses, app opens on Cleanup.
7. **No-permission path:** revoke All-Files-Access, re-add widget → widget shows "Tap to open Jupiter" and the action chips still work; no crash.
8. **Regression:** launch the app normally from the icon → splash → onboarding/permission/main exactly as before. Open Cleanup, run a scan, delete a file — unchanged. Open Vault and Search — unchanged.
9. `./gradlew :app:testDebugUnitTest` → green.

### 8.2 Recommended unit tests (JUnit + coroutines-test, already in the catalog)

`app/src/test/java/com/jupiter/filemanager/data/storage/SurfaceStorageRepositoryImplTest.kt`:

```kotlin
package com.jupiter.filemanager.data.storage

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SurfaceStorageRepositoryImplTest {

    private val provider = mock(StorageVolumeProvider::class.java)

    private fun repo() = SurfaceStorageRepositoryImpl(provider, Dispatchers.Unconfined)

    @Test
    fun `maps primary volume to snapshot`() = runTest {
        `when`(provider.volumes()).thenReturn(
            listOf(
                StorageVolumeInfo("primary", "Internal storage", "/storage/emulated/0",
                    totalBytes = 100, availableBytes = 40, isRemovable = false, isPrimary = true),
            ),
        )
        val result = repo().primarySnapshot()
        assertTrue(result is AppResult.Success)
        val snap = (result as AppResult.Success).data
        assertEquals(60L, snap.usedBytes)
        assertEquals(0.6f, snap.usedFraction)
    }

    @Test
    fun `no volume yields failure`() = runTest {
        `when`(provider.volumes()).thenReturn(emptyList())
        assertTrue(repo().primarySnapshot() is AppResult.Failure)
    }

    @Test
    fun `provider throwing yields Io failure not crash`() = runTest {
        `when`(provider.volumes()).thenThrow(RuntimeException("statfs"))
        assertTrue(repo().primarySnapshot() is AppResult.Failure)
    }
}
```

> If Mockito is not already a test dependency in the module, replace the mock with a hand-written fake `StorageVolumeProvider` subclass (it is an open class with an injected `Context`; construct it with a Robolectric/instrumented context) or extract a small functional seam. Prefer a fake to avoid adding a new test dependency — keep the catalog unchanged.

`SurfaceDeepLinkTest.kt` — assert `intent()` sets `EXTRA_DESTINATION`, `ACTION_VIEW`, and the `NEW_TASK|CLEAR_TOP|SINGLE_TOP` flags.

### 8.3 Recommended instrumented test

`app/src/androidTest/.../tile/CleanupTileServiceTest.kt` — bind the service via `ServiceTestRule` and assert `onClick()` issues a `startActivityAndCollapse` PendingIntent toward `MainActivity` (or, more simply, an espresso test that fires `SurfaceDeepLink.intent(context, DEST_CLEANUP)` and asserts the Cleanup screen renders).

---

## 9. Error handling & edge cases

1. **Storage permission not granted (no All-Files-Access).** `StorageVolumeProvider.volumes()` still returns at least the primary volume via app-private `StatFs` (no permission needed for capacity). If it returns empty, `SurfaceStorageRepositoryImpl` maps to `AppResult.Failure(AppError.NotFound)`; the widget renders the `WidgetState.Unavailable` branch ("Tap to open Jupiter") and the tile falls back to `StorageSnapshot.UNKNOWN` (0% used). Action chips still deep-link. **No crash.**
2. **`StatFs` throws (volume unmounted mid-read).** `runCatching` in the repo catches it → `AppError.Io`. Widget shows Unavailable; tile shows 0%. Verified by the unit test "provider throwing yields Io failure".
3. **Total capacity reported as 0.** `StorageSnapshot.usedFraction` guards `totalBytes <= 0` → returns `0f`; `LinearProgressIndicator(progress = 0f)` renders an empty bar, never NaN or out-of-range.
4. **Deep link arrives before nav graph is ready.** The `LaunchedEffect(pending)` re-runs whenever `deepLinkDestination` changes; `navController.navigate` is invoked once the composition (and therefore the live `NavHost`) exists. `launchSingleTop = true` prevents duplicate destinations if the user taps the same surface twice. If the route string is unrecognized, `routeFor` returns `null` and the navigation is skipped (lands on Main), so a stale/garbage extra cannot crash the app.
5. **Tile tapped while device locked.** `startActivityAndCollapse` with a `PendingIntent` (FLAG_IMMUTABLE) is the API-34-compliant call; the system handles the keyguard. The Cleanup screen itself sits behind the existing permission gate, so no unauthorized file access leaks from the lock screen.
6. **Widget update storm / rapid resize.** `provideGlance` is suspending and reads a tiny snapshot off `@IoDispatcher`; there is no scan, no recursion, no file walk. `updatePeriodMillis = 1800000` (30 min) is the platform minimum-respecting cadence, so background battery cost is negligible.
7. **API < 29 tile subtitle.** `tile.subtitle` setter exists from API 29; wrap in `runCatching` (see §7.1 note) so API 26–28 silently skip the subtitle while still showing the label and active state.
8. **Dynamic shortcut limit exceeded.** `manager.dynamicShortcuts = listOf(cleanup)` publishes exactly one dynamic shortcut, well under the system cap (typically 5), wrapped in `runCatching` so an `IllegalArgumentException` from a vendor launcher cannot crash a launch.

---

## 10. Integration with other initiatives

- **#1 Pro monetization:** the widget's "Clean up" chip and the QS tile both funnel into the Cleanup screen, a primary upsell surface. Once Pro gating exists, no change here is needed — the surfaces only *navigate*; the destination screen owns paywalls. Optionally add a Pro-only "Vault" widget chip later.
- **#2 Cloud OAuth:** independent. A future "Cloud" shortcut/widget chip can reuse `SurfaceDeepLink` by adding a `DEST_CLOUD` constant and mapping it to `Destination.CloudHub` — zero structural change.
- **#3 i18n/localization:** all user-facing widget/shortcut/tile strings are in `strings.xml` (§4.4) and `widget_*` / `shortcut_*` / `tile_*` keys are translated by initiative #3's pipeline automatically. The Glance code uses `String.format` with `Locale.getDefault()` for the byte counts.
- **Shared infra:** this initiative consumes `StorageVolumeProvider`, `AppResult/AppError`, `@IoDispatcher`, and the existing `Destination` routes — all unchanged. It adds `SurfaceStorageRepository`, which other initiatives may reuse for cheap storage readouts.
- **Ordering:** no hard dependency on any other initiative. Can ship first or last. The only cross-cutting file touched is `MainActivity.kt` (additive deep-link block); if initiative ordering creates a merge conflict there, the conflict is confined to the `onCreate`/`onNewIntent`/`routeFor` additions.

---

## 11. Rollback plan

The change is purely additive; rollback = removal, with no data migration.

1. **Manifest:** delete the `<receiver>`, the `<service>`, and the `<meta-data android:name="android.app.shortcuts">` line added in §4.3. The launcher activity and provider revert to their original form.
2. **Gradle:** remove the `androidx.glance.appwidget` line from `app/build.gradle.kts` and the two appended lines in `libs.versions.toml`.
3. **Code:** delete packages `feature/widget`, `feature/shortcuts`, `feature/tile`, plus `data/storage/SurfaceStorageRepositoryImpl.kt`, `data/storage/SurfaceEntryPoint.kt`, `di/SurfaceModule.kt`, `domain/repository/SurfaceStorageRepository.kt`, `domain/model/StorageSnapshot.kt`.
4. **`MainActivity.kt`:** revert to the original (remove the `deepLinkDestination` state, `onNewIntent`, `routeFor`, and the `LaunchedEffect`). The diff is self-contained and easy to revert with `git revert` of the feature commit.
5. **Resources:** delete `xml/jupiter_glance_widget_info.xml`, `xml/shortcuts.xml`, `layout/glance_default_loading.xml`, the four new drawables, the ProGuard block, and the appended `strings.xml` entries.

Because no Room schema, DataStore key, or remote state is introduced, removal cannot orphan user data. Any widget a user already placed becomes inert and is auto-removed by the launcher when the receiver disappears; placed tiles vanish from the shade tray.

---

## 12. Definition of done

- [ ] `androidx.glance:glance-appwidget:1.1.1` added via the version catalog; `./gradlew :app:dependencies` resolves it.
- [ ] Manifest declares `JupiterGlanceReceiver`, `CleanupTileService` (with `BIND_QUICK_SETTINGS_TILE` + QS intent-filter), and the `android.app.shortcuts` meta-data on `MainActivity`; no existing element modified except the additive meta-data line.
- [ ] All new resources present and valid: `jupiter_glance_widget_info.xml`, `shortcuts.xml`, `glance_default_loading.xml`, `ic_qs_cleanup`, `ic_shortcut_{search,cleanup,vault}`, and the appended `strings.xml` keys.
- [ ] `SurfaceStorageRepository` + `SurfaceStorageRepositoryImpl` + `SurfaceModule` + `SurfaceEntryPoint` compile; impl reuses `StorageVolumeProvider`, runs on `@IoDispatcher`, and returns `AppResult`/`AppError` (never throws).
- [ ] Glance widget renders a storage gauge + Browse/Search/Clean-up chips that deep-link via `SurfaceDeepLink`; degrades to the "Unavailable" branch with no crash when no volume/permission.
- [ ] Static shortcuts (Search/Cleanup/Vault) + the dynamic "Free up space (NN% used)" shortcut appear on launcher long-press and navigate correctly.
- [ ] QS tile shows used-% subtitle (API 29+), label + active state on all supported APIs, and opens Cleanup on tap.
- [ ] `MainActivity` deep-link routing works for all four destinations and is a strict superset of prior behavior (no extra ⇒ identical splash→main flow).
- [ ] **No regression: existing Cleanup scan/delete flow still works** (run a scan, select, delete a file) exactly as before.
- [ ] **No regression: existing Vault and Search screens still open and function** from the in-app navigation, unaffected by the new deep-link path.
- [ ] **No regression: normal cold launch from the launcher icon** still goes splash → onboarding/permission/main with no widget/tile involvement.
- [ ] Unit tests for `SurfaceStorageRepositoryImpl` (success / no-volume / throwing) and `SurfaceDeepLink` pass.
- [ ] **CI green:** `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` both succeed; `:app:assembleRelease` succeeds with the new ProGuard keep rules (no R8 stripping of widget/tile).
