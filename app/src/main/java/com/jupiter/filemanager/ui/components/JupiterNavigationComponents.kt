package com.jupiter.filemanager.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jupiter.filemanager.ui.theme.JupiterDesign

/** Stable main-tab identities shared by the inner shell and reference-style outer screens. */
enum class JupiterMainTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME("tab_home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    FILES("tab_files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder),
    RECENT("tab_recent", "Recent", Icons.Filled.History, Icons.Outlined.History),
    FAVORITES("tab_favorites", "Favorites", Icons.Filled.Star, Icons.Outlined.StarBorder),
    MORE("tab_more", "More", Icons.Filled.GridView, Icons.Outlined.GridView),
    ;

    companion object {
        fun fromRoute(route: String?): JupiterMainTab = entries.firstOrNull { it.route == route } ?: HOME
    }
}

/**
 * The persistent five-item floating navigation from the supplied layouts.
 *
 * It is intentionally stateless: inner and outer navigation hosts can use the
 * same visual component without coupling their back stacks.
 */
@Composable
fun JupiterFloatingBottomNavigation(
    selectedTab: JupiterMainTab,
    onTabSelected: (JupiterMainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
        shape = JupiterDesign.FloatingNavShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        tonalElevation = 0.dp,
        shadowElevation = 14.dp,
    ) {
        NavigationBar(
            modifier = Modifier.height(68.dp),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
        ) {
            JupiterMainTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(tab) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                        )
                    },
                    label = { Text(tab.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = JupiterDesign.TealStart.copy(alpha = 0.15f),
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
