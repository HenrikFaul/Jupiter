package com.jupiter.filemanager.data.index

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time TRIGGER for duplicate detection: registers a [ContentObserver] on the MediaStore
 * "external" files collection and, whenever the system reports a change, kicks both the
 * metadata survey and the [DedupReconciler].
 *
 * This deliberately does NOT try to resolve the changed URI into the exact new file. On many
 * devices the observer fires with the bare collection URI (or `onChange` with no URI at all),
 * so resolving a single row is unreliable and often picks the wrong file. Instead the observer
 * is a pure signal; the reconciler asks MediaStore for everything newer than its checkpoint and
 * processes exactly the new/changed files. That same reconciler also runs on app foreground and
 * periodically, so files that arrive while this observer is dead are still caught.
 *
 * Idempotent registration; a chatty burst of change events coalesces into one queued reconcile
 * (KEEP unique work) with a short local debounce on top. Best-effort throughout.
 */
@Singleton
class DownloadIndexObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexingScheduler: IndexingScheduler,
) {

    @Volatile
    private var observer: ContentObserver? = null

    @Volatile
    private var lastKickAtMs = 0L

    /** Registers the MediaStore observer. Idempotent; safe to call once from app start. */
    @Synchronized
    fun start() {
        if (observer != null) return
        val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = kick()
            override fun onChange(selfChange: Boolean) = kick()
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"),
                /* notifyForDescendants = */ true,
                obs,
            )
        }.isSuccess
        if (registered) observer = obs
        // Also reconcile once on startup so anything that arrived while we were dead is caught.
        kick()
    }

    /** Unregisters the observer. Idempotent. */
    @Synchronized
    fun stop() {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    /** Enqueues a reconcile, debounced so a MediaStore change-burst yields one queued run. */
    private fun kick() {
        val now = System.currentTimeMillis()
        if (now - lastKickAtMs < DEBOUNCE_MS) return
        lastKickAtMs = now
        indexingScheduler.ensureIndexed()
        indexingScheduler.reconcileDedupNow()
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
