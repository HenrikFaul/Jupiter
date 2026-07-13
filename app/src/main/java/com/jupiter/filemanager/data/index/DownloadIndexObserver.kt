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
 * Idempotent registration; a chatty burst of change events coalesces into one trailing reconcile
 * after the final update. Best-effort throughout.
 */
@Singleton
class DownloadIndexObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexingScheduler: IndexingScheduler,
) {

    private val handler = Handler(Looper.getMainLooper())
    private val trailingKick = Runnable { enqueueNow() }

    @Volatile
    private var observer: ContentObserver? = null

    /** Registers the MediaStore observer. Idempotent; safe to call once from app start. */
    @Synchronized
    fun start() {
        if (observer != null) return
        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleTrailingKick()
            override fun onChange(selfChange: Boolean) = scheduleTrailingKick()
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
        enqueueNow()
    }

    /** Unregisters the observer. Idempotent. */
    @Synchronized
    fun stop() {
        handler.removeCallbacks(trailingKick)
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
        observer = null
    }

    /**
     * Trailing-edge debounce: every signal moves the reconcile to shortly AFTER the final
     * MediaStore update. A leading-edge timestamp gate used to discard the download-complete
     * signal when it followed the pending-row insert within 1.5 seconds.
     */
    private fun scheduleTrailingKick() {
        handler.removeCallbacks(trailingKick)
        handler.postDelayed(trailingKick, DEBOUNCE_MS)
    }

    private fun enqueueNow() {
        indexingScheduler.ensureIndexed()
        indexingScheduler.reconcileDedupNow()
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
