package com.jupiter.filemanager.data.index

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jupiter.filemanager.data.index.dedup.DedupTier
import com.jupiter.filemanager.data.index.dedup.LayerSignal
import com.jupiter.filemanager.data.index.dedup.SimilarityLayer
import com.jupiter.filemanager.data.index.dedup.SimilarityScorer
import com.jupiter.filemanager.data.index.dedup.TypeClass
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** How a newly-arrived file matched something already on the device. */
enum class DuplicateKind {
    /** Byte-for-byte identical to an existing file (same content hash). */
    EXACT,

    /** Same picture in a different format/resolution/compression (near dHash). */
    SIMILAR,
}

/**
 * A newly-arrived file together with the existing files it duplicates, the [kind] of match, the
 * fused confidence [tier], and a human-readable [explanation] of why they matched.
 */
data class DuplicateAlert(
    val newFile: FileItem,
    val existing: List<FileItem>,
    val kind: DuplicateKind,
    val tier: DedupTier = DedupTier.EXACT,
    val explanation: String = "",
)

/**
 * Inspects one newly-arrived file for duplicates. Seam over [DuplicateDetector] so the
 * [DedupReconciler] can be unit-tested against a recording fake — no Room, no files, no
 * SharedFlow-collection timing.
 */
interface ArrivalInspector {
    /** Inspects [item]; returns the alert it produced, or null when unique/not comparable. */
    suspend fun onFileArrived(item: FileItem): DuplicateAlert?
}

/**
 * The single authority that decides whether a newly-arrived file duplicates something already
 * on the device, and surfaces it (notification + a UI-observable [Flow]).
 *
 * Both entry points funnel through here so real-time (while the app is alive) and reconciled
 * (catch-up for files that landed while the app was dead) detection behave identically:
 *  - [DownloadIndexObserver] calls [onFileArrived] the moment MediaStore signals a change;
 *  - [DedupReconciler] calls it for every file newer than the persisted checkpoint.
 *
 * Detection layers, in order (cheap → expensive, exact wins):
 *  1. index the file (so it participates in future comparisons regardless of the outcome);
 *  2. EXACT: content-hash match against same-size candidates (hashed on demand);
 *  3. SIMILAR (images only): perceptual dHash within the near threshold.
 *
 * Everything is guarded and off-main-thread. A missing POST_NOTIFICATIONS grant only drops the
 * notification — the alert still flows to the UI and the file is still indexed.
 */
@Singleton
class DuplicateDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexRepository: FileIndexRepository,
    private val perceptualHashSource: PerceptualHashSource,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ArrivalInspector {

    private val _alerts = MutableSharedFlow<DuplicateAlert>(extraBufferCapacity = 32)

    @Volatile
    private var channelReady = false

    /** Hot stream of duplicate alerts for the UI (in addition to the notification). */
    fun observeDuplicateAlerts(): Flow<DuplicateAlert> = _alerts.asSharedFlow()

    /**
     * Inspects one newly-arrived [item]: indexes it, then checks exact and (for images)
     * perceptual duplicates. Emits an alert + notification on the first match kind found.
     * Returns the alert it produced, or null when the file is unique / not comparable.
     * Never throws.
     */
    override suspend fun onFileArrived(item: FileItem): DuplicateAlert? = withContext(dispatcher) {
        runCatching {
            if (item.isDirectory) return@withContext null
            // Always index first: even a unique file must be comparable against FUTURE arrivals.
            indexRepository.indexFile(item)

            val typeClass = TypeClass.fromFileType(item.type)

            // 1) EXACT byte-identical duplicate → fused to tier EXACT (identity dominates).
            val exact = indexRepository.findContentDuplicates(item)
            if (exact.isNotEmpty()) {
                val score = SimilarityScorer.score(typeClass, exactIdentity = true, signals = emptyList())
                val copies = if (exact.size == 1) "1 file" else "${exact.size} files"
                return@withContext emit(
                    DuplicateAlert(
                        newFile = item,
                        existing = exact,
                        kind = DuplicateKind.EXACT,
                        tier = score.tier,
                        explanation = "Identical content — same bytes as $copies you already have",
                    ),
                )
            }

            // 2) SIMILAR picture (images only): fingerprint + near-dHash lookup, fused through the
            // perceptual layer so the alert carries a confidence tier + explanation.
            if (item.type == FileType.IMAGE) {
                val hash = perceptualHashSource.compute(item.path) ?: return@withContext null
                indexRepository.putPerceptualHash(item.path, hash)
                if (hash == PerceptualHash.UNHASHABLE) return@withContext null
                val similar = indexRepository.findNearDuplicateImages(
                    path = item.path,
                    hash = hash,
                    threshold = PerceptualHash.DEFAULT_NEAR_THRESHOLD,
                )
                if (similar.isNotEmpty()) {
                    // A within-threshold dHash match is a strong perceptual signal.
                    val score = SimilarityScorer.score(
                        type = TypeClass.IMAGE,
                        exactIdentity = false,
                        signals = listOf(LayerSignal(SimilarityLayer.PERCEPTUAL, 0.92)),
                    )
                    val imgs = if (similar.size == 1) "an image" else "${similar.size} images"
                    return@withContext emit(
                        DuplicateAlert(
                            newFile = item,
                            existing = similar,
                            kind = DuplicateKind.SIMILAR,
                            tier = score.tier,
                            explanation = "Same picture as $imgs you already have " +
                                "(different size or format)",
                        ),
                    )
                }
            }
            null
        }.getOrNull()
    }

    private fun emit(alert: DuplicateAlert): DuplicateAlert {
        _alerts.tryEmit(alert)
        notify(alert)
        return alert
    }

    private fun notify(alert: DuplicateAlert) {
        if (!notificationsAllowed()) return
        val count = alert.existing.size
        val (title, text, idSalt) = when (alert.kind) {
            DuplicateKind.EXACT -> Triple(
                "Duplicate detected",
                "${alert.newFile.name} — you already have " +
                    if (count == 1) "1 copy" else "$count copies",
                0,
            )
            DuplicateKind.SIMILAR -> Triple(
                "Similar image detected",
                "${alert.newFile.name} looks like " +
                    (if (count == 1) "an image" else "$count images") +
                    " you already have (same picture, different size or format)",
                SIMILAR_ID_SALT,
            )
        }
        runCatching {
            ensureChannel()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            NotificationManagerCompat.from(context)
                .notify(alert.newFile.path.hashCode() xor idSalt, notification)
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
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Duplicate alerts",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts when a newly added file duplicates existing content"
                },
            )
        }
        channelReady = true
    }

    private companion object {
        const val CHANNEL_ID = "jupiter_duplicate_alerts"
        const val SIMILAR_ID_SALT = 0x5A5A5A5A
    }
}
