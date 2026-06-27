package com.jupiter.filemanager.ui.navigation

/**
 * Type-safe navigation destinations for the Jupiter file manager.
 *
 * Each [Destination] carries the route pattern used by the Navigation Compose
 * graph. Destinations that accept arguments expose a [create] helper that
 * builds a concrete, URL-encoded route for navigation.
 */
sealed class Destination(val route: String) {

    data object Permission : Destination("permission")

    data object Home : Destination("home")

    data object Browser : Destination("browser?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "browser?path=" + android.net.Uri.encode(path)
    }

    data object Search : Destination("search")

    data object Cleanup : Destination("cleanup")

    data object Vault : Destination("vault")

    data object Settings : Destination("settings")

    data object Transfer : Destination("transfer")

    data object Preview : Destination("preview?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "preview?path=" + android.net.Uri.encode(path)
    }
}
