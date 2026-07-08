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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val structuralFingerprintSource: StructuralFingerprintSource,
    private val mediaFingerprintSource: MediaFingerprintSource,
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
                // Ensure every already-indexed image carries a fingerprint BEFORE comparing, so a
                // months-old original the background backfill has not yet reached is actually in
                // the comparison set. Without this, findNearDuplicateImages only sees rows already
                // fingerprinted, so the most common case — a freshly downloaded/recompressed copy
                // of an un-backfilled original — silently never matched (the reported bug).
                ensureImagesFingerprinted()
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

            // 3) SIMILAR text/code (near-identical after reformatting/editing) via SimHash.
            if (item.type == FileType.CODE) {
                val simHash = structuralFingerprintSource.textSimHash(item.path)
                    ?: return@withContext null
                indexRepository.putStructuralHash(item.path, simHash)
                ensureStructuralFingerprinted()
                val similar = indexRepository.findNearDuplicateText(
                    path = item.path,
                    simHash = simHash,
                    threshold = StructuralHash.TEXT_NEAR_THRESHOLD,
                )
                if (similar.isNotEmpty()) {
                    val score = SimilarityScorer.score(
                        type = typeClass,
                        exactIdentity = false,
                        signals = listOf(LayerSignal(SimilarityLayer.SEMANTIC, 0.9)),
                    )
                    val files = if (similar.size == 1) "a file" else "${similar.size} files"
                    return@withContext emit(
                        DuplicateAlert(
                            newFile = item,
                            existing = similar,
                            kind = DuplicateKind.SIMILAR,
                            tier = score.tier,
                            explanation = "Near-identical text as $files you already have " +
                                "(same content, reformatted or lightly edited)",
                        ),
                    )
                }
            }

            // 4) SAME archive contents (repacked, possibly different compression) via member-tree.
            if (item.type == FileType.ARCHIVE || item.type == FileType.APK) {
                val treeHash = structuralFingerprintSource.archiveTreeHash(item.path)
                    ?: return@withContext null
                indexRepository.putStructuralHash(item.path, treeHash)
                if (treeHash == StructuralHash.UNHASHABLE) return@withContext null
                ensureStructuralFingerprinted()
                val same = indexRepository.findSameArchiveContents(
                    path = item.path,
                    treeHash = treeHash,
                )
                if (same.isNotEmpty()) {
                    val score = SimilarityScorer.score(
                        type = typeClass,
                        exactIdentity = false,
                        signals = listOf(LayerSignal(SimilarityLayer.STRUCTURAL, 0.95)),
                    )
                    val files = if (same.size == 1) "an archive" else "${same.size} archives"
                    return@withContext emit(
                        DuplicateAlert(
                            newFile = item,
                            existing = same,
                            kind = DuplicateKind.SIMILAR,
                            tier = score.tier,
                            explanation = "Same contents as $files you already have " +
                                "(repacked or recompressed)",
                        ),
                    )
                }
            }

            // 5) SIMILAR media (video / PDF / audio): a perceptual/acoustic fingerprint from an
            // on-device decoder, compared within the same type. Same on-demand-backfill contract as
            // images so an original that predates the feature is still in the comparison set.
            val mediaAlert: DuplicateAlert? = when (item.type) {
                FileType.VIDEO -> mediaNearCheck(
                    item = item,
                    compute = mediaFingerprintSource::videoKeyframeHash,
                    find = { p, h -> indexRepository.findNearDuplicateVideo(p, h, StructuralHash.VIDEO_NEAR_THRESHOLD) },
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.9,
                    noun = "video",
                    detail = "same footage, re-encoded or recompressed",
                )
                FileType.PDF -> mediaNearCheck(
                    item = item,
                    compute = mediaFingerprintSource::pdfRenderHash,
                    find = { p, h -> indexRepository.findNearDuplicatePdf(p, h, StructuralHash.PDF_NEAR_THRESHOLD) },
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.9,
                    noun = "document",
                    detail = "same document, re-exported or scanned",
                )
                FileType.AUDIO -> mediaNearCheck(
                    item = item,
                    compute = mediaFingerprintSource::audioAcousticHash,
                    find = { p, h -> indexRepository.findNearDuplicateAudio(p, h, StructuralHash.AUDIO_NEAR_THRESHOLD) },
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.88,
                    noun = "recording",
                    detail = "same recording, re-encoded",
                )
                else -> null
            }
            if (mediaAlert != null) return@withContext mediaAlert
            null
        }.getOrNull()
    }

    /**
     * Shared VIDEO/PDF/AUDIO near-duplicate path: fingerprint the arriving file with [compute],
     * persist it, backfill the missing structural fingerprints of the same type, look up matches via
     * [find], and emit a fused SIMILAR alert. Returns the alert (so the caller can return it) or null
     * when the file is unique / not decodable. [compute]/[find] are the only type-specific bits.
     */
    private suspend fun mediaNearCheck(
        item: FileItem,
        compute: (String) -> Long?,
        find: suspend (String, Long) -> List<FileItem>,
        layer: SimilarityLayer,
        similarity: Double,
        noun: String,
        detail: String,
    ): DuplicateAlert? {
        val hash = compute(item.path) ?: return null
        indexRepository.putStructuralHash(item.path, hash)
        if (hash == StructuralHash.UNHASHABLE) return null
        ensureStructuralFingerprinted()
        val similar = find(item.path, hash)
        if (similar.isEmpty()) return null
        val score = SimilarityScorer.score(
            type = TypeClass.fromFileType(item.type),
            exactIdentity = false,
            signals = listOf(LayerSignal(layer, similarity)),
        )
        val count = if (similar.size == 1) "a $noun" else "${similar.size} ${noun}s"
        return emit(
            DuplicateAlert(
                newFile = item,
                existing = similar,
                kind = DuplicateKind.SIMILAR,
                tier = score.tier,
                explanation = "Same $noun as $count you already have ($detail)",
            ),
        )
    }

    /**
     * Fingerprints every already-indexed TEXT/CODE + ARCHIVE/APK file that still lacks a structural
     * fingerprint, so the near-duplicate comparison set is COMPLETE — a copy that arrived before the
     * feature shipped is still comparable. Same bounded, cancellable, progress-guaranteed drain as
     * [ensureImagesFingerprinted]: a transient IO failure yields null (kept for a later run) while a
     * genuinely non-comparable file is marked [StructuralHash.UNHASHABLE] so the work list shrinks.
     */
    private suspend fun ensureStructuralFingerprinted() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = indexRepository.filesNeedingStructuralHash(FINGERPRINT_BATCH)
            if (batch.isEmpty()) return
            var stored = 0
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                val fingerprint = when (file.type) {
                    FileType.CODE -> structuralFingerprintSource.textSimHash(file.path)
                    FileType.ARCHIVE, FileType.APK ->
                        structuralFingerprintSource.archiveTreeHash(file.path)
                    FileType.VIDEO -> mediaFingerprintSource.videoKeyframeHash(file.path)
                    FileType.PDF -> mediaFingerprintSource.pdfRenderHash(file.path)
                    FileType.AUDIO -> mediaFingerprintSource.audioAcousticHash(file.path)
                    else -> StructuralHash.UNHASHABLE // out of scope → mark so it leaves the list
                } ?: StructuralHash.UNHASHABLE // transient failure → mark to guarantee progress
                if (runCatching { indexRepository.putStructuralHash(file.path, fingerprint) }.isSuccess) {
                    stored++
                }
            }
            if (stored == 0) return
        }
    }

    /**
     * Fingerprints every already-indexed image that still lacks a perceptual hash, so the
     * near-duplicate comparison set is COMPLETE — an original that arrived before the feature
     * shipped (or that the periodic backfill has not yet reached) is what a newly-downloaded copy
     * gets compared against. Drains the whole "missing fingerprint" work list in bounded batches;
     * progress is persisted per row, so the first image arrival pays a one-time cost and every
     * later arrival finds nothing to do (a single cheap empty-list query). Cancellable; a decode
     * failure is marked [PerceptualHash.UNHASHABLE] so the list always shrinks and a corrupt image
     * can never loop forever. Honours cancellation; per-file failures are isolated (compute never
     * throws; the store is guarded) so one bad row can't abort the pass.
     */
    private suspend fun ensureImagesFingerprinted() {
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = indexRepository.imagesNeedingPerceptualHash(FINGERPRINT_BATCH)
            if (batch.isEmpty()) return
            var stored = 0
            for (image in batch) {
                currentCoroutineContext().ensureActive()
                val fingerprint = perceptualHashSource.compute(image.path)
                    ?: PerceptualHash.UNHASHABLE
                if (runCatching { indexRepository.putPerceptualHash(image.path, fingerprint) }.isSuccess) {
                    stored++
                }
            }
            // Termination guarantee: every row is marked (hash or UNHASHABLE), so a non-empty batch
            // normally shrinks the backlog. If NOTHING could be stored (persistent write failure),
            // the same batch would return forever — stop instead of spinning.
            if (stored == 0) return
        }
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
                "Similar file detected",
                // The alert's own explanation is type-specific (image / text / archive), so the
                // notification reads correctly whichever near-duplicate layer fired.
                "${alert.newFile.name} — ${alert.explanation}",
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

        /** Rows fingerprinted per batch while draining the perceptual-hash backlog on demand. */
        const val FINGERPRINT_BATCH = 100
    }
}
