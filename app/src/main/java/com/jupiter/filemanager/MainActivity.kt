package com.jupiter.filemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.jupiter.filemanager.domain.model.ThemeMode
import com.jupiter.filemanager.ui.navigation.Destination
import com.jupiter.filemanager.ui.navigation.JupiterNavHost
import com.jupiter.filemanager.ui.theme.JupiterTheme
import dagger.hilt.android.AndroidEntryPoint

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
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
