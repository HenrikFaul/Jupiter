package com.jupiter.filemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jupiter.filemanager.data.index.DuplicateDetector
import com.jupiter.filemanager.data.index.IndexingScheduler
import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.domain.repository.IndexStateRepository
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.navigation.JupiterNavHost
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Single-activity host for the Jupiscan app.
 *
 * Annotated with [AndroidEntryPoint] so Hilt can inject the view models obtained
 * via [hiltViewModel]. Sets up the Compose content tree: applies the persisted
 * theme preferences (mode, accent override, AMOLED-black, dynamic color) through
 * [JupiterTheme] and hosts the navigation graph starting at the splash screen,
 * which decides whether to route on to onboarding, the permission gate, or the
 * main shell.
 *
 * After the main shell is reached, a one-shot check routes to the "What's New"
 * sheet when the current build's highlights have not yet been shown; this is
 * non-blocking and never interrupts the splash/onboarding/permission start flow.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    /** A notification can arrive while this activity is already visible (singleTop). */
    private var pendingDuplicateReview by mutableStateOf(false)

    /** Schedules/keeps the background index survey alive. */
    @Inject
    lateinit var indexingScheduler: IndexingScheduler

    @Inject
    lateinit var settings: SettingsDataStore

    @Inject
    lateinit var indexStateRepository: IndexStateRepository

    /**
     * A duplicate decision and Android notification delivery are separate durable lifecycles.
     * Foreground/resume retries a decision that was detected while alerts were blocked.
     */
    @Inject
    lateinit var duplicateDetector: DuplicateDetector

    /**
     * Android 13+ requires the runtime POST_NOTIFICATIONS grant for ANY notification to be
     * visible — including the indexing foreground-progress notification and the
     * "duplicate detected" alert. Nothing ever requested it, so on modern devices every
     * notification was silently dropped. Result intentionally unused: notifications are an
     * enhancement, never a gate.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            lifecycleScope.launch {
                runCatching { duplicateDetector.retryPendingNotifications() }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingDuplicateReview = intent?.getBooleanExtra(DUPLICATE_REVIEW_EXTRA, false) == true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            runCatching {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val themeMode: ThemeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
            val accentColorArgb: Long by mainViewModel.accentColorArgb.collectAsStateWithLifecycle()
            val amoledBlack: Boolean by mainViewModel.amoledBlack.collectAsStateWithLifecycle()
            val dynamicColor: Boolean by mainViewModel.dynamicColor.collectAsStateWithLifecycle()
            val resolvedDarkTheme = when (themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !resolvedDarkTheme
                    isAppearanceLightNavigationBars = !resolvedDarkTheme
                }
            }

            JupiterTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                accentColorArgb = accentColorArgb,
                amoledBlack = amoledBlack,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val navController = rememberNavController()
                    val outerBackStackEntry by navController.currentBackStackEntryAsState()
                    val outerRoute = outerBackStackEntry?.destination?.route
                    val shouldOpenDuplicateReview = pendingDuplicateReview

                    // Widget deep link: the Favorites home-screen widget launches us with
                    // an OPEN_PATH extra; navigate straight to that folder once.
                    val widgetPath = remember {
                        intent?.getStringExtra(WIDGET_OPEN_PATH_EXTRA)?.takeIf { it.isNotBlank() }
                    }
                    LaunchedEffect(widgetPath) {
                        if (widgetPath != null) {
                            navController.navigate(Destination.Browser.create(widgetPath))
                        }
                    }

                    // One-shot: never interrupt Splash, Onboarding or Permission. The old
                    // composition-wide launch raced those gates and could put What's New in
                    // front of first-run access setup before the main shell existed.
                    LaunchedEffect(outerRoute) {
                        if (!shouldOpenDuplicateReview &&
                            (outerRoute == Destination.Main.route ||
                                outerRoute == Destination.MainTab.route)
                        ) {
                            mainViewModel.maybeShowWhatsNew {
                                navController.navigate(Destination.WhatsNew.route)
                            }
                        }
                    }

                    // A duplicate-arrival alert always leads to the real review queue.  Wait
                    // for the established main shell so a notification never bypasses the
                    // first-run permission/onboarding gates.
                    LaunchedEffect(outerRoute, shouldOpenDuplicateReview) {
                        if (shouldOpenDuplicateReview &&
                            (outerRoute == Destination.Main.route ||
                                outerRoute == Destination.MainTab.route)
                        ) {
                            pendingDuplicateReview = false
                            navController.navigate(Destination.Duplicates.route) {
                                launchSingleTop = true
                            }
                        }
                    }

                    JupiterNavHost(
                        navController = navController,
                        startDestination = Destination.Splash.route,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(DUPLICATE_REVIEW_EXTRA, false)) {
            pendingDuplicateReview = true
        }
    }

    override fun onStart() {
        super.onStart()
        // Freshness on EVERY foreground, not only process creation: if the survey has never
        // reached COMPLETE (killed mid-build, access granted after startup, etc.), re-ensure
        // it now. KEEP policy makes this idempotent — an in-flight survey is never restarted.
        lifecycleScope.launch {
            runCatching {
                val enabled = settings.indexingEnabled.first()
                if (enabled) {
                    // Foreground is the user's "give me current answers now" moment: KEEP means
                    // this never stacks, and read paths keep serving the previous complete
                    // generation while the fresh survey reconciles additions/deletes in back.
                    indexingScheduler.ensureIndexed()
                    indexingScheduler.schedulePeriodicRefresh()
                    // Keep the perceptual-fingerprint backfill converging (KEEP: no-op when
                    // one is already queued/running or nothing is missing).
                    indexingScheduler.ensurePerceptualBackfill()
                    indexingScheduler.ensureStructuralBackfill()
                    // Catch up duplicate detection on files that arrived while the app was
                    // dead — the real-time observer can't see those. Cheap when nothing is new.
                    indexingScheduler.reconcileDedupNow()
                }
            }
            // Never couple delivery retry to enabling future indexing: a duplicate may already
            // have been detected while Android notifications or the dedicated channel were off.
            runCatching { duplicateDetector.retryPendingNotifications() }
        }
    }

    companion object {
        const val WIDGET_OPEN_PATH_EXTRA = "com.jupiter.filemanager.OPEN_PATH"
        const val DUPLICATE_REVIEW_EXTRA = "com.jupiter.filemanager.OPEN_DUPLICATE_REVIEW"
    }
}
