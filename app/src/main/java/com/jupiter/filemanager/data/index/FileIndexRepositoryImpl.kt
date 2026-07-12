package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.core.util.StorageExclusions
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.IndexStats
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed implementation of [FileIndexRepository].
 *
 * All blocking IO is confined to [ioDispatcher]; observable reads are moved off
 * the collecting thread via [flowOn].
 */
@Singleton
class FileIndexRepositoryImpl @Inject constructor(
    private val dao: FileIndexDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileIndexRepository {

    override fun observeChildren(parentPath: String): Flow<List<FileItem>> =
        dao.childrenOf(parentPath)
            .map { entries -> entries.map(::toFileItem).filterNot { isExcludedResultPath(it.path) } }
            .flowOn(ioDispatcher)

    override fun search(query: String): Flow<List<FileItem>> {
        val pattern = "%" + query + "%"
        return dao.searchByName(pattern)
            // Hide any stale recycle-bin/thumbnail rows indexed before the exclusion existed, so
            // search never surfaces a file that opens to "Not found" (the survey sweeps them later).
            .map { entries -> entries.map(::toFileItem).filterNot { isExcludedResultPath(it.path) } }
            .flowOn(ioDispatcher)
    }

    override suspend fun replaceChildren(parentPath: String, items: List<FileItem>) =
        withContext(ioDispatcher) {
            if (items.isEmpty()) {
                // The directory is now empty: drop all its indexed children. (deleteStale
                // with an empty keep-list would generate an invalid SQL `NOT IN ()`.)
                dao.deleteByParent(parentPath)
                return@withContext
            }
            val now = System.currentTimeMillis()
            // Preserve a previously-computed content hash for any child whose identity
            // (size + mtime) is unchanged, so re-indexing a directory's listing (e.g. every
            // time it is browsed) never discards the hashes the survey precomputed — which
            // the downloads-dedup check and the index-backed duplicate scan both rely on.
            val existing = dao.childEntries(parentPath).associateBy { it.path }
            val entries = items.map { item ->
                val prior = existing[item.path]
                // Second-precision identity (IndexPathRewrite.identityUnchanged): MediaStore and
                // filesystem mtimes differ only in sub-second rounding for an untouched file, and
                // exact equality here used to wipe every cached fingerprint on ordinary re-browse.
                val identityKept = prior != null && IndexPathRewrite.identityUnchanged(
                    item.isDirectory, prior.sizeBytes, prior.lastModified,
                    item.sizeBytes, item.lastModified,
                )
                toEntry(item, now).copy(
                    contentHash = if (identityKept) prior?.contentHash else null,
                    // Same-content rule as the content hash: the perceptual fingerprint
                    // survives while identity is unchanged, else it is recomputed.
                    perceptualHash = if (identityKept) prior?.perceptualHash else null,
                    structuralHash = if (identityKept) prior?.structuralHash else null,
                    phash = if (identityKept) prior?.phash else null,
                    ahash = if (identityKept) prior?.ahash else null,
                    quickHash = if (identityKept) prior?.quickHash else null,
                )
            }
            dao.upsertAll(entries)
            dao.deleteStale(parentPath, items.map { it.path })
        }

    override suspend fun upsert(items: List<FileItem>) = withContext(ioDispatcher) {
        writePreservingHashes(items, generation = null)
    }

    override suspend fun upsertScanned(items: List<FileItem>, generation: Long) =
        withContext(ioDispatcher) {
            writePreservingHashes(items, generation = generation)
        }

    override suspend fun sweepStaleGenerations(generation: Long) = withContext(ioDispatcher) {
        dao.deleteStaleGenerations(generation)
    }

    /**
     * Upserts [items], preserving an already-computed content hash for any file whose
     * identity (size + mtime) is unchanged — so a re-index never discards hashes computed
     * lazily for duplicate detection. When [generation] is non-null each row is stamped with
     * it (a full survey), so a later sweep can remove rows this survey did not see; delta
     * writes pass null and keep the row's prior generation (0 for new rows).
     */
    private suspend fun writePreservingHashes(items: List<FileItem>, generation: Long?) {
        if (items.isEmpty()) return
        val now = System.currentTimeMillis()
        val existing = dao.entriesForPaths(items.map { it.path }).associateBy { it.path }
        val entries = items.map { item ->
            val prior = existing[item.path]
            val identityKept = prior != null && IndexPathRewrite.identityUnchanged(
                item.isDirectory, prior.sizeBytes, prior.lastModified,
                item.sizeBytes, item.lastModified,
            )
            toEntry(item, now).copy(
                contentHash = if (identityKept) prior?.contentHash else null,
                // Preserved on unchanged identity for the same reason as contentHash —
                // otherwise every 12 h survey would wipe all fingerprints and force the
                // backfill to re-decode the whole photo library.
                perceptualHash = if (identityKept) prior?.perceptualHash else null,
                structuralHash = if (identityKept) prior?.structuralHash else null,
                phash = if (identityKept) prior?.phash else null,
                ahash = if (identityKept) prior?.ahash else null,
                quickHash = if (identityKept) prior?.quickHash else null,
                lastSeenGeneration = generation ?: prior?.lastSeenGeneration ?: 0L,
            )
        }
        dao.upsertAll(entries)
    }

    override suspend fun indexFile(item: FileItem) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        // Preserve known hashes only when the file's identity (size + second-precision mtime)
        // is unchanged; otherwise clear them so they will be recomputed lazily.
        val unchanged = if (item.isDirectory) {
            null
        } else {
            dao.getByPath(item.path)?.takeIf {
                IndexPathRewrite.identityUnchanged(
                    item.isDirectory, it.sizeBytes, it.lastModified,
                    item.sizeBytes, item.lastModified,
                )
            }
        }
        dao.upsertAll(
            listOf(
                toEntry(item, now).copy(
                    contentHash = unchanged?.contentHash,
                    perceptualHash = unchanged?.perceptualHash,
                    structuralHash = unchanged?.structuralHash,
                    phash = unchanged?.phash,
                    ahash = unchanged?.ahash,
                    quickHash = unchanged?.quickHash,
                ),
            ),
        )
    }

    override suspend fun removeByPath(path: String) = withContext(ioDispatcher) {
        dao.deleteByPath(path)
        // Remove any subtree: match children under "path/" so siblings sharing a
        // path prefix (e.g. ".../foo" vs ".../foobar") are never affected. The
        // prefix is LIKE-escaped so that literal '_' / '%' in a directory name
        // (both common) cannot act as wildcards and over-match sibling folders
        // (e.g. deleting "photos_2024" must not purge "photosX2024").
        val prefix = escapeLike(path.trimEnd('/')) + "/"
        val descendants = dao.childPathsUnder(prefix)
        descendants.forEach { dao.deleteByPath(it) }
    }

    /**
     * Escapes SQL LIKE metacharacters so the value matches literally under an
     * `ESCAPE '\'` clause. Backslash must be escaped first.
     */
    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    override suspend fun onMovedOrRenamed(fromPath: String, toItem: FileItem) =
        withContext(ioDispatcher) {
            val oldRoot = fromPath.trimEnd('/')
            val descendantPrefix = escapeLike(oldRoot) + "/"
            val affected = dao.entriesUnder(oldRoot, descendantPrefix)
            if (affected.isEmpty()) {
                // Nothing indexed under the old path — just index the new location.
                indexFile(toItem)
                return@withContext
            }
            val newRoot = toItem.path.trimEnd('/')
            val now = System.currentTimeMillis()
            // Rewrite the WHOLE subtree's paths under the new root, preserving each row's
            // cached content hash (a rename/move does not change content). Previously this
            // dropped every descendant and re-indexed only the new root, losing the subtree
            // until the next full scan.
            val rewritten = affected.mapNotNull { entry ->
                val newPath = IndexPathRewrite.rewrite(oldRoot, newRoot, entry.path)
                    ?: return@mapNotNull null
                if (entry.path == oldRoot) {
                    // The moved/renamed root: adopt the authoritative new item's metadata,
                    // keeping the hashes only if its identity is unchanged.
                    val identityKept = IndexPathRewrite.identityUnchanged(
                        toItem.isDirectory, entry.sizeBytes, entry.lastModified,
                        toItem.sizeBytes, toItem.lastModified,
                    )
                    toEntry(toItem, now).copy(
                        contentHash = if (identityKept) entry.contentHash else null,
                        perceptualHash = if (identityKept) entry.perceptualHash else null,
                        structuralHash = if (identityKept) entry.structuralHash else null,
                        phash = if (identityKept) entry.phash else null,
                        ahash = if (identityKept) entry.ahash else null,
                        quickHash = if (identityKept) entry.quickHash else null,
                    )
                } else {
                    entry.copy(
                        path = newPath,
                        parentPath = IndexPathRewrite.parentOf(newPath) ?: "",
                        name = IndexPathRewrite.nameOf(newPath),
                    )
                }
            }
            // Drop the old rows and insert the rewritten ones ATOMICALLY (Room can't UPDATE a
            // PK). A single transaction means a process death mid-op can't lose the subtree.
            dao.replaceSubtree(affected.map { it.path }, rewritten)
        }

    override suspend fun findContentDuplicates(item: FileItem): List<FileItem> =
        withContext(ioDispatcher) {
            // Size floor: empty/near-empty files (pending downloads, .nomedia placeholders)
            // are ubiquitous and byte-identical BY CONSTRUCTION — alerting on them would spam
            // "you already have N copies" for every started download. Matches the 4 KiB floor
            // the cleanup dedup paths use.
            if (item.isDirectory || item.sizeBytes < MIN_ALERT_SIZE_BYTES) {
                return@withContext emptyList()
            }

            val hash = dao.hashIfUnchanged(item.path, item.sizeBytes, item.lastModified)
                ?: computeHash(item.path)?.also { putHash(item, it) }
                ?: return@withContext emptyList()

            // The survey indexes METADATA only, so the pre-existing copy of a file usually
            // has no stored hash yet — byHash() alone would silently miss it and the
            // "you already have this" alert would never fire for exactly the common case
            // (an original that was never hashed). Exact duplicates must have the same
            // byte size, so hash-on-demand only the same-size candidates (cheap: size
            // collisions are rare) before the hash lookup.
            for (entry in dao.filesOfSize(item.sizeBytes)) {
                currentCoroutineContext().ensureActive()
                if (entry.path == item.path || entry.isDirectory || entry.contentHash != null) {
                    continue
                }
                hashForEntry(entry) // computes and caches as a side effect
            }

            existingOrPruned(
                dao.byHash(hash)
                    .asSequence()
                    .filter { it.path != item.path }
                    .map(::toFileItem)
                    .toList(),
            )
        }

    /**
     * Keeps only [items] whose file is still present on disk AND not in a trash/recycle-bin
     * staging dir, pruning every rejected row from the index. A duplicate alert (or the
     * duplicates list) must never point at a file that has been moved to the trash or deleted:
     * tapping it opened the preview to a "Not found: …/Android/.Trash/…" error. Vanished rows
     * are also swept here so a stale index self-heals the moment it is consulted.
     */
    private suspend fun existingOrPruned(items: List<FileItem>): List<FileItem> {
        if (items.isEmpty()) return items
        val alive = ArrayList<FileItem>(items.size)
        for (fileItem in items) {
            val usable = !isExcludedResultPath(fileItem.path) &&
                runCatching { File(fileItem.path).isFile }.getOrDefault(false)
            if (usable) alive.add(fileItem) else dao.deleteByPath(fileItem.path)
        }
        return alive
    }

    /**
     * True when [path] lives in a trash/recycle-bin/thumbnail staging directory that must never
     * be surfaced as a duplicate. Mirrors the indexing exclusions (case-insensitive, full-segment)
     * so rows that were indexed BEFORE those exclusions were added are still filtered out at read
     * time — without waiting for the next full survey to sweep them.
     */
    private fun isExcludedResultPath(path: String): Boolean {
        return StorageExclusions.isExcluded(path)
    }

    /**
     * Streams the file at [path] through a chunked SHA-1 digest. Cancellable and
     * total: any IO error (missing file, permission denied) yields null rather
     * than throwing, so best-effort callers never fail on a bad file.
     */
    private suspend fun computeHash(path: String): String? {
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val digest = MessageDigest.getInstance("SHA-1")
            val buffer = ByteArray(DEFAULT_HASH_BUFFER)
            file.inputStream().use { stream ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (read > 0) digest.update(buffer, 0, read)
                }
            }
            digest.digest().toHexString()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    /** Lowercase, unseparated hex, matching the app's existing SHA-1 hash format. */
    private fun ByteArray.toHexString(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    override suspend fun hashForUnchanged(
        path: String,
        sizeBytes: Long,
        lastModified: Long,
    ): String? = withContext(ioDispatcher) {
        dao.hashIfUnchanged(path, sizeBytes, lastModified)
    }

    override suspend fun putHash(item: FileItem, hash: String) = withContext(ioDispatcher) {
        val now = System.currentTimeMillis()
        if (dao.getByPath(item.path) != null) {
            // Targeted UPDATE: never rewrite the whole row from a snapshot — a concurrent
            // survey's generation stamp must survive (see updateHash).
            dao.updateHash(item.path, item.sizeBytes, item.lastModified, hash, now)
        } else {
            dao.upsertAll(listOf(toEntry(item, now).copy(contentHash = hash)))
        }
    }

    override suspend fun putPerceptualHash(path: String, hash: Long) = withContext(ioDispatcher) {
        dao.updatePerceptualHash(path, hash)
    }

    override suspend fun putPerceptualFingerprint(
        path: String,
        dhash: Long,
        phash: Long,
        ahash: Long,
    ) = withContext(ioDispatcher) {
        dao.updatePerceptualFingerprint(path = path, dhash = dhash, phash = phash, ahash = ahash)
    }

    override suspend fun putStructuralHash(path: String, hash: Long) = withContext(ioDispatcher) {
        dao.updateStructuralHash(path, hash)
    }

    override suspend fun findNearDuplicateText(
        path: String,
        simHash: Long,
        threshold: Int,
    ): List<FileItem> = withContext(ioDispatcher) {
        if (simHash == StructuralHash.UNHASHABLE) return@withContext emptyList()
        val nearPaths = dao.structuralHashesOfTypes(TEXT_TYPE_NAMES, StructuralHash.UNHASHABLE)
            .asSequence()
            .filter { it.path != path }
            .filter {
                com.jupiter.filemanager.data.index.dedup.TextSimHash
                    .isNear(simHash, it.structuralHash, threshold)
            }
            .map { it.path }
            .toList()
        if (nearPaths.isEmpty()) return@withContext emptyList()
        existingOrPruned(dao.entriesForPaths(nearPaths).map(::toFileItem))
    }

    override suspend fun findSameArchiveContents(
        path: String,
        treeHash: Long,
    ): List<FileItem> = withContext(ioDispatcher) {
        if (treeHash == StructuralHash.UNHASHABLE) return@withContext emptyList()
        val samePaths = dao.structuralHashesOfTypes(ARCHIVE_TYPE_NAMES, StructuralHash.UNHASHABLE)
            .asSequence()
            .filter { it.path != path }
            .filter { it.structuralHash == treeHash } // equal member-tree = same contents
            .map { it.path }
            .toList()
        if (samePaths.isEmpty()) return@withContext emptyList()
        existingOrPruned(dao.entriesForPaths(samePaths).map(::toFileItem))
    }

    override suspend fun findNearDuplicateVideo(
        path: String,
        hash: Long,
        threshold: Int,
    ): List<FileItem> = withContext(ioDispatcher) {
        nearByStructuralHamming(path, hash, threshold, VIDEO_TYPE_NAMES)
    }

    override suspend fun findNearDuplicatePdf(
        path: String,
        hash: Long,
        threshold: Int,
    ): List<FileItem> = withContext(ioDispatcher) {
        nearByStructuralHamming(path, hash, threshold, PDF_TYPE_NAMES)
    }

    override suspend fun findNearDuplicateAudio(
        path: String,
        hash: Long,
        threshold: Int,
    ): List<FileItem> = withContext(ioDispatcher) {
        nearByStructuralHamming(path, hash, threshold, AUDIO_TYPE_NAMES)
    }

    /**
     * Shared near-lookup for the perceptual/dHash-style structural fingerprints (video keyframe, PDF
     * render, audio envelope): scan the lean (path, structuralHash) projection of the given types in
     * memory, keep those within [threshold] Hamming distance, then materialize + existence-prune the
     * few matches. The UNHASHABLE sentinel is excluded by the query.
     */
    private suspend fun nearByStructuralHamming(
        path: String,
        hash: Long,
        threshold: Int,
        typeNames: List<String>,
    ): List<FileItem> {
        if (hash == StructuralHash.UNHASHABLE) return emptyList()
        val nearPaths = dao.structuralHashesOfTypes(typeNames, StructuralHash.UNHASHABLE)
            .asSequence()
            .filter { it.path != path }
            .filter { java.lang.Long.bitCount(hash xor it.structuralHash) <= threshold }
            .map { it.path }
            .toList()
        if (nearPaths.isEmpty()) return emptyList()
        return existingOrPruned(dao.entriesForPaths(nearPaths).map(::toFileItem))
    }

    override suspend fun filesNeedingStructuralHash(limit: Int): List<FileItem> =
        withContext(ioDispatcher) {
            dao.filesMissingStructuralHash(STRUCTURAL_TYPE_NAMES, limit).map(::toFileItem)
        }

    override suspend fun findNearDuplicateImages(
        path: String,
        hash: Long,
        threshold: Int,
    ): List<FileItem> = withContext(ioDispatcher) {
        if (hash == PerceptualHash.UNHASHABLE) return@withContext emptyList()
        // Two-step: compare against the lean (path, hash) projection in memory — tens of
        // thousands of 64-bit fingerprints are trivial to scan — then materialize full rows
        // for the few matches only.
        val nearPaths = dao.allPerceptualHashes(PerceptualHash.UNHASHABLE)
            .asSequence()
            .filter { it.path != path }
            .filter { PerceptualHash.isNear(hash, it.perceptualHash, threshold) }
            .map { it.path }
            .toList()
        if (nearPaths.isEmpty()) return@withContext emptyList()
        existingOrPruned(dao.entriesForPaths(nearPaths).map(::toFileItem))
    }

    override suspend fun imagesNeedingPerceptualHash(limit: Int): List<FileItem> =
        withContext(ioDispatcher) {
            dao.imagesMissingPerceptualHash(FileType.IMAGE.name, limit).map(::toFileItem)
        }

    override suspend fun imagesNeedingPerceptualHashCount(): Int = withContext(ioDispatcher) {
        dao.countImagesMissingPerceptualHash(FileType.IMAGE.name)
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        dao.clear()
    }

    override fun stats(): Flow<IndexStats> =
        dao.count()
            .map { count ->
                IndexStats(indexedCount = count, lastIndexedAt = dao.maxIndexedAt() ?: 0L)
            }
            .flowOn(ioDispatcher)

    // --- Index-backed analytics ----------------------------------------------
    // (Completeness is IndexStateRepository's job — no row-count "isPopulated" here.)

    override suspend fun largeFiles(minSizeBytes: Long, limit: Int): List<FileItem> =
        withContext(ioDispatcher) {
            // Never surface trash/recycle-bin rows (e.g. Samsung `Android/.Trash/…`) in the
            // Large-files list — opening one errors with "Not found". Prune stale rows on sight.
            existingSkippingExcluded(dao.largeFiles(minSizeBytes, limit).map(::toFileItem))
        }

    override suspend fun allFiles(): List<FileItem> = withContext(ioDispatcher) {
        dao.allFiles().map(::toFileItem).filterNot { isExcludedResultPath(it.path) }
    }

    /**
     * Filters out (and prunes) rows in a trash/thumbnail dir. Unlike [existingOrPruned] this does
     * NOT stat every file (the large-files list can be long and on-disk checks would be costly); it
     * only drops the excluded-segment paths, which are the ones that open to "Not found".
     */
    private suspend fun existingSkippingExcluded(items: List<FileItem>): List<FileItem> {
        val kept = ArrayList<FileItem>(items.size)
        for (item in items) {
            if (isExcludedResultPath(item.path)) dao.deleteByPath(item.path) else kept.add(item)
        }
        return kept
    }

    override suspend fun indexedPaths(): Set<String> = withContext(ioDispatcher) {
        dao.allPaths().toHashSet()
    }

    override suspend fun pathsAtGeneration(generation: Long): Set<String> =
        withContext(ioDispatcher) {
            dao.pathsAtGeneration(generation).toHashSet()
        }

    override suspend fun duplicateGroups(minSizeBytes: Long): List<DuplicateGroup> =
        withContext(ioDispatcher) {
            val groups = mutableListOf<DuplicateGroup>()
            for (size in dao.collidingSizes(minSizeBytes)) {
                currentCoroutineContext().ensureActive()
                val candidates = dao.filesOfSize(size)
                if (candidates.size < 2) continue

                // CASCADE stage 2 — QUICK HASH pre-filter: same-size candidates are first split by
                // a cheap head+tail hash (128 KiB of IO per file max, persisted in `quickHash`), so
                // the expensive full-content hash below only ever runs on files whose quick hash
                // ALREADY collides. Two big same-size-but-different files (common for videos/DB
                // files) now cost two short reads instead of two full-file reads.
                val byQuick = LinkedHashMap<String, MutableList<FileIndexEntry>>()
                for (entry in candidates) {
                    currentCoroutineContext().ensureActive()
                    // Trash/recycle-bin rows are byte-identical to their originals but must not be
                    // offered for deletion (opening one errors) — prune and skip them.
                    if (isExcludedResultPath(entry.path)) {
                        dao.deleteByPath(entry.path)
                        continue
                    }
                    val quick = quickHashForEntry(entry) ?: continue
                    byQuick.getOrPut(quick) { mutableListOf() }.add(entry)
                }

                // CASCADE stage 3 — STRONG full-content hash, only within quick-hash collisions.
                val byHash = LinkedHashMap<String, MutableList<FileIndexEntry>>()
                for (quickGroup in byQuick.values) {
                    if (quickGroup.size < 2) continue
                    for (entry in quickGroup) {
                        currentCoroutineContext().ensureActive()
                        val hash = hashForEntry(entry) ?: continue
                        byHash.getOrPut(hash) { mutableListOf() }.add(entry)
                    }
                }
                for ((hash, group) in byHash) {
                    if (group.size < 2) continue
                    groups += DuplicateGroup(hash = hash, files = group.map(::toFileItem))
                }
            }
            groups
        }

    /**
     * Returns the head+tail quick hash for [entry], reusing the stored value while the file's
     * identity (size + second-precision mtime) is unchanged, else computing and persisting it.
     * SHA-1 over the first and last [QUICK_HASH_WINDOW] bytes — a pre-FILTER only (grouping is
     * always confirmed by the full-content hash), so collision strength is irrelevant here.
     * Null when the file vanished or cannot be read.
     */
    private suspend fun quickHashForEntry(entry: FileIndexEntry): String? {
        val file = File(entry.path)
        val currentSize = runCatching { if (file.isFile) file.length() else -1L }.getOrDefault(-1L)
        if (currentSize < 0L) return null
        val currentMtime = runCatching { file.lastModified() }.getOrDefault(0L)
        val identityUnchanged = currentSize == entry.sizeBytes &&
            currentMtime / 1000 == entry.lastModified / 1000
        if (identityUnchanged && entry.quickHash != null) return entry.quickHash

        val computed = computeQuickHash(file) ?: return null
        runCatching { dao.updateQuickHash(entry.path, computed) }
        return computed
    }

    /** SHA-1 over the first and last [QUICK_HASH_WINDOW] bytes of [file] (whole file if smaller). */
    private suspend fun computeQuickHash(file: File): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-1")
            val length = file.length()
            java.io.RandomAccessFile(file, "r").use { raf ->
                val head = ByteArray(minOf(QUICK_HASH_WINDOW.toLong(), length).toInt())
                raf.readFully(head)
                digest.update(head)
                currentCoroutineContext().ensureActive()
                if (length > QUICK_HASH_WINDOW * 2L) {
                    raf.seek(length - QUICK_HASH_WINDOW)
                    val tail = ByteArray(QUICK_HASH_WINDOW)
                    raf.readFully(tail)
                    digest.update(tail)
                }
            }
            // Prefix with the length so equal head+tail of different-length files never collide
            // (defensive only — callers group within one size bucket anyway).
            "$length:" + digest.digest().toHexString()
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun nearDuplicateImageGroups(threshold: Int): List<DuplicateGroup> =
        withContext(ioDispatcher) {
            // Every fingerprinted image as a lean (path, dHash) pair — scanning tens of thousands of
            // 64-bit hashes in memory is trivial.
            val entries = dao.allPerceptualHashes(PerceptualHash.UNHASHABLE)
            val n = entries.size
            if (n < 2) return@withContext emptyList()

            // Union-find over the image set. Two passes build the clusters:
            val parent = IntArray(n) { it }
            val rank = IntArray(n)
            fun find(x: Int): Int {
                var r = x
                while (parent[r] != r) {
                    parent[r] = parent[parent[r]] // path halving
                    r = parent[r]
                }
                return r
            }
            fun union(a: Int, b: Int) {
                val ra = find(a)
                val rb = find(b)
                if (ra == rb) return
                when {
                    rank[ra] < rank[rb] -> parent[ra] = rb
                    rank[ra] > rank[rb] -> parent[rb] = ra
                    else -> { parent[rb] = ra; rank[ra]++ }
                }
            }

            // Pass 1 — exact dHash equality (O(n)). dHash is resolution/compression-robust, so a
            // re-saved photo usually lands on the IDENTICAL hash; this alone catches the common case
            // and scales to any library size.
            val firstOfHash = HashMap<Long, Int>(n)
            for (i in 0 until n) {
                val prev = firstOfHash.putIfAbsent(entries[i].perceptualHash, i)
                if (prev != null) union(i, prev)
            }

            // Pass 2 — near-merge via LSH banding, so near copies whose dHash is a few bits off also
            // group (a re-encode/resize can shift 1–3 bits). Split the 64-bit hash into 8 one-byte
            // bands; two hashes within [threshold] Hamming distance necessarily share ≥1 band
            // UNCHANGED whenever fewer than 8 bits differ (pigeonhole across 8 bands) — which real
            // near-dups always are. Only same-band candidates are compared, so the work is ~n per
            // band instead of the n² that made a 40k-image library skip this step entirely. A
            // comparison budget backstops any pathological skew, and already-unioned pairs are
            // skipped (identical-hash copies were merged in pass 1).
            if (threshold > 0) {
                var budget = MAX_NEAR_PAIR_COMPARISONS
                bands@ for (band in 0 until 8) {
                    val shift = band * 8
                    val buckets = HashMap<Int, MutableList<Int>>()
                    for (i in 0 until n) {
                        val key = ((entries[i].perceptualHash ushr shift) and 0xFFL).toInt()
                        buckets.getOrPut(key) { ArrayList() }.add(i)
                    }
                    for (bucket in buckets.values) {
                        if (bucket.size < 2) continue
                        currentCoroutineContext().ensureActive()
                        for (a in bucket.indices) {
                            val ia = bucket[a]
                            for (b in (a + 1) until bucket.size) {
                                if (budget-- <= 0L) break@bands
                                val ib = bucket[b]
                                if (find(ia) == find(ib)) continue // already same cluster
                                // STACKED verification: a near-dHash candidate only merges when
                                // the weighted dHash+pHash+aHash score agrees (falls back to
                                // dHash-only for legacy rows without the extra layers) — no
                                // single hash family decides a match.
                                if (PerceptualHash.isVisuallySimilar(
                                        entries[ia].perceptualHash, entries[ib].perceptualHash,
                                        entries[ia].phash, entries[ib].phash,
                                        entries[ia].ahash, entries[ib].ahash,
                                        dhashThreshold = threshold,
                                    )
                                ) {
                                    union(ia, ib)
                                }
                            }
                        }
                    }
                }
            }

            // Collect clusters of two or more, then materialize + existence-prune the few that matter.
            val clusters = LinkedHashMap<Int, MutableList<String>>()
            for (i in 0 until n) {
                clusters.getOrPut(find(i)) { mutableListOf() }.add(entries[i].path)
            }
            val out = ArrayList<DuplicateGroup>()
            for (paths in clusters.values) {
                if (paths.size < 2) continue
                currentCoroutineContext().ensureActive()
                val alive = existingOrPruned(dao.entriesForPaths(paths).map(::toFileItem))
                if (alive.size < 2) continue
                // Largest first so "keep the best" / "select extras" defaults to the highest-res copy.
                val sorted = alive.sortedByDescending { it.sizeBytes }
                out += DuplicateGroup(
                    hash = "img:" + sorted.first().path,
                    files = sorted,
                    similar = true,
                )
            }
            out
        }

    override suspend fun hashCollidingSizes(minSizeBytes: Long) = withContext(ioDispatcher) {
        for (size in dao.collidingSizes(minSizeBytes)) {
            currentCoroutineContext().ensureActive()
            val candidates = dao.filesOfSize(size)
            if (candidates.size < 2) continue
            for (entry in candidates) {
                currentCoroutineContext().ensureActive()
                if (entry.contentHash != null) continue
                hashForEntry(entry) // computes and caches as a side effect
            }
        }
    }

    /**
     * Returns the content hash for an indexed [entry], safe to use for
     * duplicate-grouping that may drive a delete.
     *
     * The stored hash is trusted ONLY when the file on disk STILL matches the
     * indexed identity (same size AND mtime) — this guards against a file that
     * changed outside the app without a delta firing, which would otherwise let two
     * genuinely-different files be grouped as duplicates from a stale hash. When the
     * identity no longer matches (or no hash is stored yet) the file is re-hashed
     * from its current bytes and the index row is refreshed with the current
     * size/mtime/hash. Returns null when the file has vanished or cannot be read, so
     * a stale entry is never grouped.
     */
    private suspend fun hashForEntry(entry: FileIndexEntry): String? {
        val file = File(entry.path)
        val currentSize = runCatching { if (file.isFile) file.length() else -1L }
            .getOrDefault(-1L)
        if (currentSize < 0L) return null // vanished or not a regular file → never group
        val currentMtime = runCatching { file.lastModified() }.getOrDefault(0L)

        // Second-precision mtime: MediaStore-seeded rows carry whole seconds while the live stat
        // reports millis — exact equality would treat every such row as modified and re-hash it.
        val identityUnchanged = currentSize == entry.sizeBytes &&
            currentMtime / 1000 == entry.lastModified / 1000
        if (identityUnchanged) {
            entry.contentHash?.let { return it }
        }

        // Stale identity or no cached hash: hash the CURRENT bytes and refresh the row
        // (size/mtime/hash) so the confirmation always reflects what is on disk now.
        // Targeted UPDATE, not a whole-row upsert: hashing takes real time, and a survey
        // running concurrently may have re-stamped this row's lastSeenGeneration — writing
        // back a pre-hash snapshot would revert that stamp and the sweep would wrongly
        // delete a live file's row.
        val computed = computeHash(entry.path) ?: return null
        dao.updateHash(
            path = entry.path,
            sizeBytes = currentSize,
            lastModified = currentMtime,
            hash = computed,
            indexedAt = System.currentTimeMillis(),
        )
        return computed
    }

    // --- Mapping -------------------------------------------------------------

    private fun toFileItem(entry: FileIndexEntry): FileItem = FileItem(
        path = entry.path,
        name = entry.name,
        isDirectory = entry.isDirectory,
        sizeBytes = entry.sizeBytes,
        lastModified = entry.lastModified,
        type = runCatching { FileType.valueOf(entry.typeName) }.getOrDefault(FileType.OTHER),
        extension = entry.extension,
        mimeType = null,
        isHidden = entry.name.startsWith("."),
        childCount = null,
    )

    private fun toEntry(item: FileItem, indexedAt: Long): FileIndexEntry = FileIndexEntry(
        path = item.path,
        parentPath = item.parentPath ?: "",
        name = item.name,
        sizeBytes = item.sizeBytes,
        lastModified = item.lastModified,
        typeName = item.type.name,
        isDirectory = item.isDirectory,
        extension = item.extension,
        contentHash = null,
        indexedAt = indexedAt,
    )

    private companion object {
        /** 64 KiB read window for streamed hashing. */
        const val DEFAULT_HASH_BUFFER = 64 * 1024

        /** Head and tail window (each) of the quick pre-filter hash: 64 KiB + 64 KiB per file. */
        const val QUICK_HASH_WINDOW = 64 * 1024

        /**
         * Budget on the LSH near-image merge's candidate comparisons (cheap XOR/bitcount each).
         * LSH keeps the real count far below this; the budget only backstops a pathologically skewed
         * hash distribution, after which pass-1 exact-dHash grouping still stands. ~200M ≈ a second.
         */
        const val MAX_NEAR_PAIR_COMPARISONS = 200_000_000L

        /**
         * Files below this size never trigger a duplicate ALERT: empty/near-empty files
         * (in-flight download placeholders, .nomedia markers) are byte-identical by
         * construction and would spam false "you already have this" notifications.
         * Matches the cleanup dedup floor (4 KiB).
         */
        const val MIN_ALERT_SIZE_BYTES = 4L * 1024

        /** Lowercase hex alphabet used by [toHexString]. */
        val HEX_DIGITS = "0123456789abcdef".toCharArray()

        /** Types carrying a text SimHash structural fingerprint. */
        val TEXT_TYPE_NAMES = listOf(FileType.CODE.name)

        /** Types carrying an archive member-tree structural fingerprint. */
        val ARCHIVE_TYPE_NAMES = listOf(FileType.ARCHIVE.name, FileType.APK.name)

        /** Types carrying a media dHash / envelope structural fingerprint. */
        val VIDEO_TYPE_NAMES = listOf(FileType.VIDEO.name)
        val PDF_TYPE_NAMES = listOf(FileType.PDF.name)
        val AUDIO_TYPE_NAMES = listOf(FileType.AUDIO.name)

        /** Every type that gets a structural fingerprint (backfill work list scope). */
        val STRUCTURAL_TYPE_NAMES =
            TEXT_TYPE_NAMES + ARCHIVE_TYPE_NAMES + VIDEO_TYPE_NAMES + PDF_TYPE_NAMES + AUDIO_TYPE_NAMES
    }
}
