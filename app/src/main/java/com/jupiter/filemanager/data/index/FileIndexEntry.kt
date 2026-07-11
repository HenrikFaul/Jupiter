package com.jupiter.filemanager.data.index

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Room row representing a single indexed file-system entry.
 *
 * Rows are populated by the background indexing pass and read back by search
 * (and, later, duplicate scans) so callers can avoid re-walking storage.
 *
 * @property path absolute path; primary key.
 * @property parentPath absolute path of the containing directory.
 * @property typeName the [com.jupiter.filemanager.domain.model.FileType] name.
 * @property contentHash optional content hash, populated by a later pass.
 * @property indexedAt epoch-millis timestamp of when this row was written.
 */
@Entity(
    tableName = "file_index",
    indices = [
        Index("parentPath"),
        Index("name"),
        // Hot dedup/analytics query columns — without these, collidingSizes/filesOfSize/byHash/
        // largeFiles and the generation sweep full-scan a 100k-row table on every call.
        Index("sizeBytes"),
        Index("contentHash"),
        Index("perceptualHash"),
        Index("lastSeenGeneration"),
        Index("typeName"),
    ],
)
data class FileIndexEntry(
    @PrimaryKey val path: String,
    val parentPath: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val typeName: String,
    val isDirectory: Boolean,
    val extension: String,
    val contentHash: String? = null,
    val indexedAt: Long,
    /**
     * The scan generation that last saw this row. A full survey stamps every row it writes
     * with its generation; after the survey succeeds, rows carrying an older generation are
     * globally swept (they no longer exist). 0 = written outside a full survey (e.g. a delta).
     */
    val lastSeenGeneration: Long = 0L,
    /**
     * 64-bit dHash of the image's luminance structure ([PerceptualHash]), for NEAR-duplicate
     * detection across formats/resolutions. Null = not computed yet (backfilled in the
     * background); [PerceptualHash.UNHASHABLE] = tried but undecodable (never retried).
     * Only meaningful for image files.
     */
    val perceptualHash: Long? = null,
    /**
     * 64-bit non-perceptual near-duplicate fingerprint, meaningful for TEXT/CODE (a formatting-
     * insensitive [com.jupiter.filemanager.data.index.dedup.TextSimHash]) and ARCHIVE/APK (a
     * member-tree fingerprint). Compared ONLY within the same file type, so the shared column is
     * unambiguous. Null = not computed yet; [StructuralHash.UNHASHABLE] = tried but not comparable
     * (never retried, never matched). See [StructuralFingerprintSource].
     */
    val structuralHash: Long? = null,
    /**
     * Cheap head+tail pre-filter hash (SHA-1 over the first and last 64 KiB), computed lazily for
     * same-size duplicate candidates so the expensive full-content hash only runs on files whose
     * quick hash already collides. Null = not computed yet.
     */
    val quickHash: String? = null,
    /**
     * DCT perceptual hash ([PerceptualHash.pHashFromLuminanceGrid]) — the second layer of the
     * stacked image fingerprint. Null on rows fingerprinted before this layer existed (the
     * comparison falls back to dHash-only); [PerceptualHash.UNHASHABLE] = undecodable.
     */
    val phash: Long? = null,
    /**
     * Average hash ([PerceptualHash.aHashFromLuminanceGrid]) — the third stacked layer.
     * Same null/UNHASHABLE semantics as [phash].
     */
    val ahash: Long? = null,
)
