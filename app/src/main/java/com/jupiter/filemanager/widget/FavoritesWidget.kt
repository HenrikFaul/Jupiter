package com.jupiter.filemanager.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jupiter.filemanager.R
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Hilt entry point used to obtain the [BookmarkRepository] from within the Glance
 * widget. Glance widgets/receivers are not part of the standard Hilt injection graph,
 * so dependencies are pulled from the application component on demand via
 * [EntryPointAccessors.fromApplication].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun bookmarkRepository(): BookmarkRepository
}

/**
 * Home-screen widget that lists the user's favorite folders/files (bookmarks).
 * Tapping a row launches [MainActivity][com.jupiter.filemanager.MainActivity] with an
 * [EXTRA_OPEN_PATH] extra so the app can navigate directly to that path.
 */
class FavoritesWidget : GlanceAppWidget() {

    companion object {
        /** Intent extra key carrying the absolute path a widget tap should open. */
        const val EXTRA_OPEN_PATH = "com.jupiter.filemanager.OPEN_PATH"

        /** Action parameter key mirroring [EXTRA_OPEN_PATH] for per-row click actions. */
        val OpenPathKey = ActionParameters.Key<String>(EXTRA_OPEN_PATH)

        /** Maximum number of favorites rendered in the widget. */
        private const val MAX_ROWS = 8
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Read bookmarks off the widget's provideGlance coroutine (not the main thread).
        val favorites: List<Bookmark> = runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            entryPoint.bookmarkRepository()
                .observeBookmarks()
                .first()
        }.getOrElse { emptyList() }

        // Capture the runtime package (applicationId, ".debug" on debug builds) so the
        // launch Intents resolve MainActivity on whichever variant is installed.
        val packageName = context.packageName
        provideContent {
            WidgetContent(favorites, packageName)
        }
    }

    @Composable
    private fun WidgetContent(favorites: List<Bookmark>, packageName: String) {
        // Day/night factory lives in androidx.glance.color; fully-qualified so it does
        // not collide with the androidx.glance.unit.ColorProvider interface used as the
        // FavoriteRow parameter type above.
        val bg = androidx.glance.color.ColorProvider(day = Color(0xFFFFFBFE), night = Color(0xFF1C1B1F))
        val onBg = androidx.glance.color.ColorProvider(day = Color(0xFF1C1B1F), night = Color(0xFFFFFBFE))
        val subtle = androidx.glance.color.ColorProvider(day = Color(0xFF49454F), night = Color(0xFFCAC4D0))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            // Header — tapping it opens the app at the top level.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(openAppIntent(packageName))),
            ) {
                Image(
                    provider = ImageProvider(R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = GlanceModifier.width(20.dp).height(20.dp),
                )
                Spacer(GlanceModifier.width(8.dp))
                Text(
                    text = "Favorites",
                    style = TextStyle(
                        color = onBg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            if (favorites.isEmpty()) {
                Text(
                    text = "Add favorites in Jupiter",
                    style = TextStyle(color = subtle, fontSize = 13.sp),
                    maxLines = 2,
                )
            } else {
                favorites.take(MAX_ROWS).forEach { bookmark ->
                    FavoriteRow(bookmark, onBg, subtle, packageName)
                    Spacer(GlanceModifier.height(2.dp))
                }
            }
        }
    }

    @Composable
    private fun FavoriteRow(
        bookmark: Bookmark,
        textColor: ColorProvider,
        iconColor: ColorProvider,
        packageName: String,
    ) {
        val display = bookmark.label.ifBlank { File(bookmark.path).name.ifBlank { bookmark.path } }
        val isDir = runCatching { File(bookmark.path).isDirectory }.getOrDefault(true)
        val iconRes = if (isDir) {
            android.R.drawable.ic_menu_more
        } else {
            android.R.drawable.ic_menu_save
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable(
                    actionStartActivity(
                        openPathIntent(bookmark.path, packageName),
                        actionParametersOf(OpenPathKey to bookmark.path),
                    ),
                ),
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(iconColor),
                modifier = GlanceModifier.width(18.dp).height(18.dp),
            )
            Spacer(GlanceModifier.width(10.dp))
            Text(
                text = display,
                style = TextStyle(color = textColor, fontSize = 14.sp),
                maxLines = 1,
            )
        }
    }

    /** Intent that opens the app with a specific path to navigate to. */
    private fun openPathIntent(path: String, packageName: String): Intent =
        Intent().apply {
            // Use the runtime package (applicationId) — it carries the ".debug" suffix
            // on the debug build, so a hard-coded "com.jupiter.filemanager" would fail
            // to resolve MainActivity on the installed debug APK.
            setClassName(packageName, "com.jupiter.filemanager.MainActivity")
            action = Intent.ACTION_VIEW
            addCategory(Intent.CATEGORY_DEFAULT)
            putExtra(EXTRA_OPEN_PATH, path)
            // Ensure a distinct PendingIntent per path (extras are not part of the
            // PendingIntent equality check, so vary the data URI).
            data = android.net.Uri.parse("jupiter://open/" + android.net.Uri.encode(path))
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

    /** Intent that opens the app at its default top-level screen. */
    private fun openAppIntent(packageName: String): Intent =
        Intent().apply {
            setClassName(packageName, "com.jupiter.filemanager.MainActivity")
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
