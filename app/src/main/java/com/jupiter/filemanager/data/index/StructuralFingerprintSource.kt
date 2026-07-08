package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.data.index.dedup.TextSimHash
import java.io.File
import java.util.zip.ZipException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sentinels + thresholds for the structural / near-duplicate fingerprints that are NOT perceptual
 * (images) and NOT exact content hashes — i.e. the text/code SimHash layer and the archive
 * member-tree layer. Kept 64-bit so both share the single [FileIndexEntry.structuralHash] column
 * (they are only ever compared WITHIN the same file type, so the shared column is unambiguous).
 */
object StructuralHash {
    /**
     * "Tried but not comparable" (unreadable, not a real container, binary-in-a-text-extension).
     * Persisted so the backfill work-list always shrinks and a bad file is never retried forever;
     * EXCLUDED from every match query so two unrelated unhashable files never falsely match.
     */
    const val UNHASHABLE: Long = Long.MIN_VALUE

    /** Text/code SimHash near-duplicate threshold (Hamming distance of 64). */
    const val TEXT_NEAR_THRESHOLD: Int = TextSimHash.DEFAULT_NEAR_THRESHOLD

    /**
     * Perceptual (dHash) near thresholds for the media layers (Hamming of 64). Video/PDF reuse the
     * image dHash scale; a slightly looser video bound absorbs re-encode/keyframe jitter. Audio is
     * a loudness-envelope hash, so its threshold is tuned independently.
     */
    const val VIDEO_NEAR_THRESHOLD: Int = 10
    const val PDF_NEAR_THRESHOLD: Int = 8
    const val AUDIO_NEAR_THRESHOLD: Int = 10
}

/**
 * Computes the non-perceptual near-duplicate fingerprints:
 *
 *  - **Text/code** (`FileType.CODE`): a formatting-insensitive 64-bit [TextSimHash] so a reformatted,
 *    re-indented, or lightly-edited copy of a source/config/markup file is still recognised as the
 *    same text (a content hash shatters on any edit; SimHash survives it).
 *  - **Archives** (`FileType.ARCHIVE` / `FileType.APK`, ZIP-family): a 64-bit fingerprint of the
 *    sorted `(name, uncompressed-size, crc)` member set, so the SAME files repacked with different
 *    compression (different bytes → different content hash) are recognised as the same contents.
 *
 * All computation is pure JVM (no Android graphics/media), off the main thread, and total: any IO
 * error yields null (retry later) and a structurally-uncomparable file yields [StructuralHash.UNHASHABLE]
 * (mark once, never retry, never match). This mirrors [PerceptualHashSource] for images.
 */
@Singleton
class StructuralFingerprintSource @Inject constructor() {

    /**
     * 64-bit SimHash of the text at [path]. Returns [TextSimHash.EMPTY] for empty/binary content
     * (never matches anything) and null only for a transient IO failure worth retrying.
     */
    fun textSimHash(path: String): Long? {
        return try {
            val file = File(path)
            if (!file.isFile) return null
            val bytes = readPrefix(file, MAX_TEXT_BYTES) ?: return null
            // A NUL byte means this is binary content wearing a text extension — not comparable as
            // text; mark EMPTY so it is stored (leaves the work list) yet never matches.
            if (bytes.isEmpty() || bytes.any { it.toInt() == 0 }) return TextSimHash.EMPTY
            TextSimHash.of(String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 64-bit fingerprint of a ZIP-family archive's member set at [path]. Returns
     * [StructuralHash.UNHASHABLE] when the file is not a readable ZIP container (e.g. tar/gz/7z/rar —
     * conservatively never matched) or is empty, and null only for a transient IO failure.
     */
    fun archiveTreeHash(path: String): Long? {
        return try {
            val file = File(path)
            if (!file.isFile) return null
            ZipFile(file).use { zip ->
                val members = ArrayList<String>()
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    // (name, uncompressed size, crc): identity of the member independent of how it
                    // was compressed. Sorted below so member order in the central directory is
                    // irrelevant.
                    members.add(entry.name + '|' + entry.size + '|' + entry.crc)
                }
                if (members.isEmpty()) return StructuralHash.UNHASHABLE
                members.sort()
                fnv64(members)
            }
        } catch (_: ZipException) {
            StructuralHash.UNHASHABLE // not a ZIP container → conservative: never claims a match
        } catch (_: Exception) {
            null
        }
    }

    /** Reads up to [limit] bytes from [file] without relying on API-33+ `readNBytes`. */
    private fun readPrefix(file: File, limit: Int): ByteArray? {
        file.inputStream().use { stream ->
            val out = java.io.ByteArrayOutputStream(minOf(limit, 64 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (total < limit) {
                val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
                if (read < 0) break
                out.write(buffer, 0, read)
                total += read
            }
            return out.toByteArray()
        }
    }

    /** FNV-1a 64-bit over the concatenated members with a record separator (no external deps). */
    private fun fnv64(members: List<String>): Long {
        var h = FNV_OFFSET
        for (member in members) {
            for (ch in member) {
                h = h xor ch.code.toLong()
                h *= FNV_PRIME
            }
            h = h xor RECORD_SEPARATOR
            h *= FNV_PRIME
        }
        // Never collide with the UNHASHABLE sentinel for a real archive.
        return if (h == StructuralHash.UNHASHABLE) h + 1 else h
    }

    private companion object {
        /** 1 MiB prefix is far more than a SimHash needs and bounds cost on huge logs. */
        const val MAX_TEXT_BYTES = 1 shl 20
        const val FNV_OFFSET = -0x340d631b7bdddcdbL // 0xcbf29ce484222325 as a signed Long
        const val FNV_PRIME = 0x100000001b3L
        const val RECORD_SEPARATOR = 0x0aL
    }
}
