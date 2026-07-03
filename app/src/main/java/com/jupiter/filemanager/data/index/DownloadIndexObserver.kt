package com.jupiter.filemanager.data.index

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * A newly-arrived file (e.g. a completed download) together with the existing
 * indexed entries whose content is byte-for-byte identical to it.
 */
data class DuplicateAlert(
    val newFile: FileItem,
    val existing: List<FileItem>,
)

/**
 * Keeps the persistent file index LIVE for files that appear outside of in-app
 * operations — most importantly downloads and other media added by other apps.
 *
 * Registers an [ContentObserver] on the MediaStore "external" files collection.
 * When the system reports a change, the referenced path is resolved, indexed
 * immediately (a delta, not a rescan), and checked for a CONTENT duplicate via
 * [FileIndexRepository.findContentDuplicates]. If a duplicate is found the user
 * is told "you already have this" — through a notification (when
 * `POST_NOTIFICATIONS` is granted) and via [observeDuplicateAlerts] so the UI
 * can surface it too.
 *
 * Everything is best-effort and fully guarded: a failure to resolve, index, hash,
 * or notify is swallowed and never propagates. All work runs off the main thread
 * on the injected IO dispatcher.
 */
@Singleton
class DownloadIndexObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexRepository: FileIndexRepository,
    private val fileSystemDataSource: FileSystemDataSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _duplicateAlerts = MutableSharedFlow<DuplicateAlert>(extraBufferCapacity = 16)

    /** Coalesces rapid repeated notifications for the same path (MediaStore is chatty). */
    private val recentlyHandled = ConcurrentHashMap<String, Long>()

    @Volatile
    private var observer: ContentObserver? = null

    @Volatile
    private var channelReady = false

    /**
     * A hot stream of duplicate alerts. The UI may collect this to show an
     * in-app "you already have this" prompt in addition to the notification.
     */
    fun observeDuplicateAlerts(): Flow<DuplicateAlert> = _duplicateAlerts.asSharedFlow()

    /** Registers the MediaStore observer. Idempotent; safe to call once from app start. */
    @Synchronized
    fun start() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                handleChange(uri)
            }

            override fun onChange(selfChange: Boolean) {
                handleChange(null)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                /* notifyForDescendants = */ true,
                obs,
            )
        }.isSuccess
        if (registered) observer = obs
    }

    /** Unregisters the observer and stops responding to changes. Idempotent. */
    @Synchronized
    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    private fun handleChange(uri: Uri?) {
        // Offload everything; onChange may run on the main looper.
        scope.launch {
            runCatching {
                val path = resolvePath(uri) ?: return@launch
                if (isDebounced(path)) return@launch

                val file = File(path)
                if (!file.exists() || file.isDirectory) return@launch

                val item = fileSystemDataSource.toFileItem(file)
                indexRepository.indexFile(item)

                val duplicates = indexRepository.findContentDuplicates(item)
                if (duplicates.isNotEmpty()) {
                    _duplicateAlerts.tryEmit(DuplicateAlert(item, duplicates))
                    notifyDuplicate(item, duplicates.size)
                }
            }
        }
    }

    /** True if [path] was handled within the debounce window (and records this attempt). */
    private fun isDebounced(path: String): Boolean {
        val now = System.currentTimeMillis()
        val last = recentlyHandled.put(path, now)
        return last != null && now - last < DEBOUNCE_MS
    }

    /**
     * Resolves the filesystem path backing [uri] by querying MediaStore for its
     * DATA column. Returns null when the uri is absent, unqueryable, or has no
     * usable path (e.g. a bare collection uri).
     */
    private fun resolvePath(uri: Uri?): String? {
        if (uri == null) return null
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val column = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (column < 0) return null
                cursor.getString(column)?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun notifyDuplicate(item: FileItem, count: Int) {
        if (!notificationsAllowed()) return
        runCatching {
            ensureChannel()
            val copies = if (count == 1) "1 copy" else "$count copies"
            val text = "Duplicate detected: ${item.name} — you already have $copies"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle("Duplicate detected")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context)
                .notify(item.path.hashCode(), notification)
        }
    }

    private fun notificationsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        if (channelReady) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager != null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Duplicate alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when a newly added file duplicates existing content"
                }
                manager.createNotificationChannel(channel)
            }
        }
        channelReady = true
    }

    private companion object {
        const val CHANNEL_ID = "jupiter_duplicate_alerts"
        const val DEBOUNCE_MS = 1_500L
    }
}
