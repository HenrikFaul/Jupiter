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

    data object PdfViewer : Destination("pdf?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "pdf?path=" + android.net.Uri.encode(path)
    }

    data object TextEditor : Destination("text_editor?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "text_editor?path=" + android.net.Uri.encode(path)
    }

    // ---- Startup & shell ----

    data object Splash : Destination("splash")

    data object Onboarding : Destination("onboarding")

    data object Main : Destination("main")

    // ---- AI & analytics ----

    data object AiAssistant : Destination("ai_assistant")

    data object StorageAnalytics : Destination("storage_analytics")

    data object Duplicates : Destination("duplicates")

    data object Downloads : Destination("downloads")

    data object Trash : Destination("trash")

    /**
     * Instant, device-wide category listing backed by MediaStore.
     *
     * The [ARG_TYPE] argument carries a [com.jupiter.filemanager.domain.model.StorageCategory]
     * name (e.g. "IMAGES"); [create] builds the concrete route from a category.
     */
    data object CategoryBrowse : Destination("category?type={type}") {
        const val ARG_TYPE = "type"
        fun create(category: com.jupiter.filemanager.domain.model.StorageCategory): String =
            "category?type=" + category.name
    }

    // ---- Organization ----

    data object Tags : Destination("tags")

    data object Workspaces : Destination("workspaces")

    data object WorkspaceDetail : Destination("workspace/{id}") {
        const val ARG_ID = "id"
        fun create(id: String): String = "workspace/" + android.net.Uri.encode(id)
    }

    data object FileDetails : Destination("file_details?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "file_details?path=" + android.net.Uri.encode(path)
    }

    // ---- Transfer & connectivity ----

    data object TransferCenter : Destination("transfer_center")

    data object NearbyTransfer : Destination("nearby_transfer")

    data object WifiTransfer : Destination("wifi_transfer")

    data object CloudHub : Destination("cloud_hub")

    data object NasConnections : Destination("nas_connections")

    data object RemoteBrowser : Destination("remote_browser?connectionId={connectionId}&path={path}") {
        const val ARG_CONNECTION = "connectionId"
        const val ARG_PATH = "path"
        fun create(connectionId: String, path: String): String =
            "remote_browser?connectionId=" + android.net.Uri.encode(connectionId) +
                "&path=" + android.net.Uri.encode(path)
    }

    // ---- Privacy & automation ----

    data object PrivacyDashboard : Destination("privacy_dashboard")

    data object DataTransparency : Destination("data_transparency")

    data object Automation : Destination("automation")

    data object RuleBuilder : Destination("rule_builder")

    // ---- Archive & media ----

    data object ArchiveManagerRoute : Destination("archive?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "archive?path=" + android.net.Uri.encode(path)
    }

    /** Device-aware media compression (image + video). */
    data object Compress : Destination("compress")

    /** Image albums grouped by MediaStore bucket/folder. */
    data object Albums : Destination("albums")

    data object MusicPlayer : Destination("music?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "music?path=" + android.net.Uri.encode(path)
    }

    data object VideoPlayer : Destination("video?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "video?path=" + android.net.Uri.encode(path)
    }

    data object ImageGallery : Destination("gallery?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "gallery?path=" + android.net.Uri.encode(path)
    }

    // ---- Versioning & sync ----

    data object VersionHistory : Destination("versions?path={path}") {
        const val ARG_PATH = "path"
        fun create(path: String): String = "versions?path=" + android.net.Uri.encode(path)
    }

    data object SyncConflicts : Destination("sync_conflicts")

    data object DualPane : Destination("dual_pane")

    // ---- Growth: monetization & onboarding ----

    data object Paywall : Destination("paywall")

    data object WhatsNew : Destination("whats_new")
}
