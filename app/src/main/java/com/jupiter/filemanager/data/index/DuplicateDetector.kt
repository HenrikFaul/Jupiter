package com.jupiter.filemanager.data.index

import android.content.Context
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
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
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
sealed interface ArrivalInspectionResult {
    data object Unique : ArrivalInspectionResult
    data class Alerted(val alert: DuplicateAlert) : ArrivalInspectionResult
    data class Retry(val reason: String, val cause: Throwable? = null) : ArrivalInspectionResult
}

interface ArrivalInspector {
    /** Inspects [item] without conflating a unique file with a transient processing failure. */
    suspend fun inspectArrival(item: FileItem): ArrivalInspectionResult

    /** Compatibility seam for direct callers that only need the optional alert. */
    suspend fun onFileArrived(item: FileItem): DuplicateAlert? =
        when (val result = inspectArrival(item)) {
            is ArrivalInspectionResult.Alerted -> result.alert
            is ArrivalInspectionResult.Unique, is ArrivalInspectionResult.Retry -> null
        }
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
    @ApplicationContext context: Context,
    private val indexRepository: FileIndexRepository,
    private val perceptualHashSource: PerceptualHashSource,
    private val structuralFingerprintSource: StructuralFingerprintSource,
    private val mediaFingerprintSource: MediaFingerprintSource,
    private val dedupDecisionDao: DedupDecisionDao,
    private val notificationPublisher: DuplicateNotificationPublisher,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : ArrivalInspector {

    /** Convenience constructor retained for focused tests and non-Hilt callers. */
    constructor(
        context: Context,
        indexRepository: FileIndexRepository,
        perceptualHashSource: PerceptualHashSource,
        structuralFingerprintSource: StructuralFingerprintSource,
        mediaFingerprintSource: MediaFingerprintSource,
        dedupDecisionDao: DedupDecisionDao,
        dispatcher: CoroutineDispatcher,
    ) : this(
        context,
        indexRepository,
        perceptualHashSource,
        structuralFingerprintSource,
        mediaFingerprintSource,
        dedupDecisionDao,
        AndroidDuplicateNotificationPublisher(context),
        dispatcher,
    )

    private val _alerts = MutableSharedFlow<DuplicateAlert>(extraBufferCapacity = 32)

    /** Hot stream of duplicate alerts for the UI (in addition to the notification). */
    fun observeDuplicateAlerts(): Flow<DuplicateAlert> = _alerts.asSharedFlow()

    /** Read-only delivery diagnostics for permission/settings guidance surfaces. */
    fun notificationDeliveryHealth(): DuplicateNotificationHealth = notificationPublisher.health()

    /**
     * Inspects one newly-arrived [item]: indexes it, then checks exact and (for images)
     * perceptual duplicates. Emits an alert + notification on the first match kind found.
     * Returns a typed outcome so durable cursors never mistake a transient IO/decode failure for
     * a unique file. Cancellation still propagates normally.
     */
    override suspend fun inspectArrival(item: FileItem): ArrivalInspectionResult =
        withContext(dispatcher) {
            try {
                // A prior decision may have been persisted while Android notifications were
                // blocked. Every real arrival is a cheap opportunity to retry one collapsed batch.
                retryPendingNotificationsInContext()
                val alert = detectArrival(item)
                if (alert == null) {
                    ArrivalInspectionResult.Unique
                } else {
                    ArrivalInspectionResult.Alerted(alert)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                ArrivalInspectionResult.Retry(
                    reason = error.message ?: error::class.java.simpleName,
                    cause = error,
                )
            }
        }

    private suspend fun detectArrival(item: FileItem): DuplicateAlert? {
        if (item.isDirectory) return null
        // Always index first: even a unique file must be comparable against FUTURE arrivals.
        indexRepository.indexFile(item)

        val typeClass = TypeClass.fromFileType(item.type)

        // 1) EXACT byte-identical duplicate → fused to tier EXACT (identity dominates).
        val exact = indexRepository.findContentDuplicates(item)
        if (exact.isNotEmpty()) {
                val score = SimilarityScorer.score(typeClass, exactIdentity = true, signals = emptyList())
                val copies = if (exact.size == 1) "1 file" else "${exact.size} files"
                return emit(
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
                val fp = perceptualHashSource.computeAll(item.path)
                    ?: throw TransientArrivalException("Image fingerprint temporarily unavailable")
                indexRepository.putPerceptualFingerprint(
                    item.path, fp.dhash, fp.phash, fp.ahash, fp.width, fp.height,
                )
                val hash = fp.dhash
                if (hash == PerceptualHash.UNHASHABLE) return null
                // Ensure every already-indexed image carries a fingerprint BEFORE comparing, so a
                // months-old original the background backfill has not yet reached is actually in
                // the comparison set. Without this, findNearDuplicateImages only sees rows already
                // fingerprinted, so the most common case — a freshly downloaded/recompressed copy
                // of an un-backfilled original — silently never matched (the reported bug).
                if (!ensureImagesFingerprinted()) {
                    throw TransientArrivalException("Existing image fingerprint coverage incomplete")
                }
                val similar = indexRepository.findNearDuplicateImages(
                    path = item.path,
                    fingerprint = fp,
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
                    return emit(
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
                    ?: throw TransientArrivalException("Text fingerprint temporarily unavailable")
                indexRepository.putStructuralHash(item.path, simHash)
                if (!ensureStructuralFingerprinted()) {
                    throw TransientArrivalException("Existing structural fingerprint coverage incomplete")
                }
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
                    return emit(
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
                    ?: throw TransientArrivalException("Archive fingerprint temporarily unavailable")
                indexRepository.putStructuralHash(item.path, treeHash)
                if (treeHash == StructuralHash.UNHASHABLE) return null
                if (!ensureStructuralFingerprinted()) {
                    throw TransientArrivalException("Existing structural fingerprint coverage incomplete")
                }
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
                    return emit(
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
                    compute = mediaFingerprintSource::videoFingerprint,
                    find = indexRepository::findNearDuplicateVideo,
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.9,
                    noun = "video",
                    detail = "same footage, re-encoded or recompressed",
                )
                FileType.PDF -> mediaNearCheck(
                    item = item,
                    compute = mediaFingerprintSource::pdfFingerprint,
                    find = indexRepository::findNearDuplicatePdf,
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.9,
                    noun = "document",
                    detail = "same document, re-exported or scanned",
                )
                FileType.AUDIO -> mediaNearCheck(
                    item = item,
                    compute = mediaFingerprintSource::audioFingerprint,
                    find = indexRepository::findNearDuplicateAudio,
                    layer = SimilarityLayer.PERCEPTUAL,
                    similarity = 0.88,
                    noun = "recording",
                    detail = "same recording, re-encoded",
                )
                else -> null
            }
            if (mediaAlert != null) return mediaAlert
            return null
    }

    /**
     * Shared VIDEO/PDF/AUDIO near-duplicate path: fingerprint the arriving file with [compute],
     * persist it, backfill the missing structural fingerprints of the same type, look up matches via
     * [find], and emit a fused SIMILAR alert. Returns the alert (so the caller can return it) or null
     * when the file is unique / not decodable. [compute]/[find] are the only type-specific bits.
     */
    private suspend fun mediaNearCheck(
        item: FileItem,
        compute: (String) -> MediaFingerprint?,
        find: suspend (String, MediaFingerprint) -> List<FileItem>,
        layer: SimilarityLayer,
        similarity: Double,
        noun: String,
        detail: String,
    ): DuplicateAlert? {
        val fingerprint = compute(item.path)
            ?: throw TransientArrivalException("Media fingerprint temporarily unavailable")
        indexRepository.putMediaFingerprint(item.path, fingerprint)
        if (fingerprint.primaryHash == StructuralHash.UNHASHABLE) return null
        if (!ensureStructuralFingerprinted()) {
            throw TransientArrivalException("Existing media fingerprint coverage incomplete")
        }
        val similar = find(item.path, fingerprint)
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
    private suspend fun ensureStructuralFingerprinted(): Boolean {
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = indexRepository.filesNeedingStructuralHash(FINGERPRINT_BATCH)
            if (batch.isEmpty()) return true
            var stored = 0
            var transientFailures = 0
            for (file in batch) {
                currentCoroutineContext().ensureActive()
                val storedResult = when (file.type) {
                    FileType.CODE -> structuralFingerprintSource.textSimHash(file.path)?.let { hash ->
                        runCatching { indexRepository.putStructuralHash(file.path, hash) }
                    }
                    FileType.ARCHIVE, FileType.APK ->
                        structuralFingerprintSource.archiveTreeHash(file.path)?.let { hash ->
                            runCatching { indexRepository.putStructuralHash(file.path, hash) }
                        }
                    FileType.VIDEO -> mediaFingerprintSource.videoFingerprint(file.path)?.let { fp ->
                        runCatching { indexRepository.putMediaFingerprint(file.path, fp) }
                    }
                    FileType.PDF -> mediaFingerprintSource.pdfFingerprint(file.path)?.let { fp ->
                        runCatching { indexRepository.putMediaFingerprint(file.path, fp) }
                    }
                    FileType.AUDIO -> mediaFingerprintSource.audioFingerprint(file.path)?.let { fp ->
                        runCatching { indexRepository.putMediaFingerprint(file.path, fp) }
                    }
                    else -> runCatching {
                        indexRepository.putStructuralHash(file.path, StructuralHash.UNHASHABLE)
                    }
                }
                if (storedResult == null) {
                    transientFailures++
                    continue
                }
                if (storedResult.isSuccess) {
                    stored++
                } else {
                    transientFailures++
                }
        }
            if (transientFailures > 0) return false
            if (stored == 0) return false
        }
    }

    /**
     * Fingerprints every already-indexed image that still lacks a perceptual hash, so the
     * near-duplicate comparison set is COMPLETE — an original that arrived before the feature
     * shipped (or that the periodic backfill has not yet reached) is what a newly-downloaded copy
     * gets compared against. Drains the whole "missing fingerprint" work list in bounded batches;
     * each decoded page is persisted in one Room transaction, so the first image arrival pays a
     * one-time cost without triggering one readiness aggregate refresh per row, and every
     * later arrival finds nothing to do (a single cheap empty-list query). Cancellable; a decode
     * failure is marked [PerceptualHash.UNHASHABLE] so the list always shrinks and a corrupt image
     * can never loop forever. Honours cancellation; per-file failures are isolated (compute never
     * throws; the store is guarded) so one bad row can't abort the pass.
     */
    private suspend fun ensureImagesFingerprinted(): Boolean {
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = indexRepository.imagesNeedingPerceptualHash(FINGERPRINT_BATCH)
            if (batch.isEmpty()) return true
            val updates = ArrayList<PathPerceptualFingerprint>(batch.size)
            var transientFailures = 0
            for (image in batch) {
                currentCoroutineContext().ensureActive()
                val fp = perceptualHashSource.computeAll(image.path)
                if (fp == null) {
                    transientFailures++
                    continue
                }
                updates += PathPerceptualFingerprint(image.path, fp)
            }
            val stored = if (updates.isEmpty()) {
                0
            } else {
                try {
                    indexRepository.putPerceptualFingerprints(updates)
                    updates.size
                } catch (cancellation: kotlinx.coroutines.CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    transientFailures++
                    0
                }
            }
            // Termination guarantee: every row is marked (hash or UNHASHABLE), so a non-empty batch
            // normally shrinks the backlog. If NOTHING could be stored (persistent write failure),
            // the same batch would return forever — stop instead of spinning.
            if (transientFailures > 0) return false
            if (stored == 0) return false
        }
    }

    private suspend fun emit(alert: DuplicateAlert): DuplicateAlert? {
        val decision = recordDecision(alert) ?: return null
        _alerts.tryEmit(alert)
        deliverNewDecision(alert, decision)
        return alert
    }

    private suspend fun recordDecision(alert: DuplicateAlert): DedupDecision? {
        val existingPaths = alert.existing.map { it.path }.distinct().sorted()
        val key = canonicalDecisionKey(
            newPath = alert.newFile.path,
            existingPaths = existingPaths,
            kind = alert.kind,
        )
        val decision = DedupDecision(
            decisionKey = key,
            kind = alert.kind.name,
            newPath = alert.newFile.path,
            existingPaths = existingPaths.joinToString(separator = "\n"),
            algorithmVersion = DECISION_ALGORITHM_VERSION,
            createdAt = System.currentTimeMillis(),
        )
        return decision.takeIf { dedupDecisionDao.insert(it) != -1L }
    }

    private fun canonicalDecisionKey(
        newPath: String,
        existingPaths: List<String>,
        kind: DuplicateKind,
    ): String {
        val canonicalPaths = (existingPaths + newPath).sorted()
        val raw = buildString {
            append(kind.name)
            append('|')
            append(DECISION_ALGORITHM_VERSION)
            canonicalPaths.forEach {
                append('|')
                append(it)
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    /**
     * Retries a bounded pending batch as one summary notification. Safe to call from Activity
     * foreground/permission callbacks and observer paths; DAO claiming prevents double delivery.
     */
    suspend fun retryPendingNotifications() = withContext(dispatcher) {
        retryPendingNotificationsInContext()
    }

    private suspend fun retryPendingNotificationsInContext() {
        val attemptedAt = System.currentTimeMillis()
        val claimed = dedupDecisionDao.claimDeliveryBatch(
            attemptedAt = attemptedAt,
            staleBefore = attemptedAt - DELIVERY_CLAIM_TIMEOUT_MS,
            limit = MAX_PENDING_SUMMARY,
        )
        if (claimed.isEmpty()) return
        val result = runCatching {
            notificationPublisher.publishPendingSummary(claimed)
        }.getOrElse { error ->
            NotificationDeliveryResult(
                status = NotificationDeliveryStatus.FAILED,
                detail = error.message ?: error::class.java.simpleName,
            )
        }
        persistDeliveryResult(claimed.map { it.decisionKey }, result)
    }

    private suspend fun deliverNewDecision(alert: DuplicateAlert, decision: DedupDecision) {
        val attemptedAt = System.currentTimeMillis()
        val claimed = dedupDecisionDao.claimDelivery(
            decisionKey = decision.decisionKey,
            attemptedAt = attemptedAt,
            staleBefore = attemptedAt - DELIVERY_CLAIM_TIMEOUT_MS,
        )
        if (claimed != 1) return
        val result = runCatching {
            notificationPublisher.publish(alert)
        }.getOrElse { error ->
            NotificationDeliveryResult(
                status = NotificationDeliveryStatus.FAILED,
                detail = error.message ?: error::class.java.simpleName,
            )
        }
        persistDeliveryResult(listOf(decision.decisionKey), result)
    }

    private suspend fun persistDeliveryResult(
        decisionKeys: List<String>,
        result: NotificationDeliveryResult,
    ) {
        when (result.status) {
            NotificationDeliveryStatus.DELIVERED ->
                dedupDecisionDao.markDelivered(decisionKeys, System.currentTimeMillis())
            NotificationDeliveryStatus.BLOCKED ->
                dedupDecisionDao.releaseDelivery(
                    decisionKeys,
                    DedupDeliveryState.BLOCKED.name,
                    result.detail,
                )
            NotificationDeliveryStatus.FAILED ->
                dedupDecisionDao.releaseDelivery(
                    decisionKeys,
                    DedupDeliveryState.FAILED.name,
                    result.detail,
                )
        }
    }

    private companion object {
        const val DECISION_ALGORITHM_VERSION = 1
        const val MAX_PENDING_SUMMARY = 100
        const val DELIVERY_CLAIM_TIMEOUT_MS = 5 * 60 * 1000L

        /** Rows fingerprinted per batch while draining the perceptual-hash backlog on demand. */
        const val FINGERPRINT_BATCH = 100
    }

    private class TransientArrivalException(message: String) : Exception(message)
}
