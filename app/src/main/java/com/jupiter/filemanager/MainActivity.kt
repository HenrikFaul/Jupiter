package com.jupiter.filemanager

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
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
 * Single-activity host for Jupiter.
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
class MainActivity : FragmentActivity() {

    /** Schedules/keeps the background index survey alive. */
    @Inject
    lateinit var indexingScheduler: IndexingScheduler

    @Inject
    lateinit var settings: SettingsDataStore

    @Inject
    lateinit var indexStateRepository: IndexStateRepository

    /**
     * Android 13+ requires the runtime POST_NOTIFICATIONS grant for ANY notification to be
     * visible — including the indexing foreground-progress notification and the
     * "duplicate detected" alert. Nothing ever requested it, so on modern devices every
     * notification was silently dropped. Result intentionally unused: notifications are an
     * enhancement, never a gate.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

                    // One-shot: surface the "What's New" sheet for this build once the
                    // main shell has been reached. Runs after first composition only;
                    // the view model persists the seen version so it never repeats.
                    LaunchedEffect(Unit) {
                        mainViewModel.maybeShowWhatsNew {
                            navController.navigate(Destination.WhatsNew.route)
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

    override fun onStart() {
        super.onStart()
        // Freshness on EVERY foreground, not only process creation: if the survey has never
        // reached COMPLETE (killed mid-build, access granted after startup, etc.), re-ensure
        // it now. KEEP policy makes this idempotent — an in-flight survey is never restarted.
        lifecycleScope.launch {
            runCatching {
                val enabled = settings.indexingEnabled.first()
                if (enabled && !indexStateRepository.isMetadataComplete()) {
                    indexingScheduler.ensureIndexed()
                }
                if (enabled) {
                    // Keep the perceptual-fingerprint backfill converging (KEEP: no-op when
                    // one is already queued/running or nothing is missing).
                    indexingScheduler.ensurePerceptualBackfill()
                    // Catch up duplicate detection on files that arrived while the app was
                    // dead — the real-time observer can't see those. Cheap when nothing is new.
                    indexingScheduler.reconcileDedupNow()
                }
            }
        }
    }

    private companion object {
        const val WIDGET_OPEN_PATH_EXTRA = "com.jupiter.filemanager.OPEN_PATH"
    }
}
