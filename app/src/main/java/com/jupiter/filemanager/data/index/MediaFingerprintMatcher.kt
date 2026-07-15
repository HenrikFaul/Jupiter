package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.domain.model.FileType
import kotlin.math.abs
import kotlin.math.max

/**
 * Conservative, type-aware confirmation for media candidates. The old implementation compared one
 * 64-bit value for every media type; a shared black/title frame was therefore enough to merge
 * unrelated videos. These gates deliberately prefer a missed review candidate over a destructive
 * false positive.
 */
object MediaFingerprintMatcher {

    fun matches(type: FileType, a: MediaFingerprint, b: MediaFingerprint): Boolean {
        if (a.version != MediaFingerprint.CURRENT_VERSION || b.version != MediaFingerprint.CURRENT_VERSION) {
            return false
        }
        if (a.hashes.any { it == StructuralHash.UNHASHABLE } ||
            b.hashes.any { it == StructuralHash.UNHASHABLE }
        ) return false
        return when (type) {
            FileType.VIDEO -> videoMatches(a, b)
            FileType.PDF -> pdfMatches(a, b)
            FileType.AUDIO -> audioMatches(a, b)
            else -> false
        }
    }

    private fun videoMatches(a: MediaFingerprint, b: MediaFingerprint): Boolean {
        if (!durationsCompatible(a.extent, b.extent)) return false
        val distances = alignedInformativeDistances(a.hashes, b.hashes)
        if (distances.size < MIN_VIDEO_EVIDENCE_FRAMES) return false
        val mean = distances.average()
        val close = distances.count { it <= VIDEO_FRAME_CLOSE_THRESHOLD }
        return mean <= VIDEO_MEAN_THRESHOLD &&
            distances.maxOrNull()!! <= VIDEO_MAX_FRAME_THRESHOLD &&
            close * 5 >= distances.size * 4 // at least 80% of informative samples agree closely
    }

    private fun pdfMatches(a: MediaFingerprint, b: MediaFingerprint): Boolean {
        // A page-count mismatch is a hard veto for "same document". One-page PDFs are valid and
        // therefore need only one sample; longer documents are confirmed across sampled pages.
        if (a.extent == null || b.extent == null || a.extent != b.extent) return false
        val distances = alignedInformativeDistances(a.hashes, b.hashes)
        val required = if (a.extent == 1L) 1 else minOf(2, a.hashes.size, b.hashes.size)
        return distances.size >= required &&
            distances.average() <= PDF_MEAN_THRESHOLD &&
            distances.maxOrNull()!! <= PDF_MAX_PAGE_THRESHOLD
    }

    private fun audioMatches(a: MediaFingerprint, b: MediaFingerprint): Boolean {
        if (!durationsCompatible(a.extent, b.extent)) return false
        val ah = a.hashes.singleOrNull() ?: return false
        val bh = b.hashes.singleOrNull() ?: return false
        if (!isInformative(ah) || !isInformative(bh)) return false
        return java.lang.Long.bitCount(ah xor bh) <= AUDIO_ENVELOPE_THRESHOLD
    }

    private fun alignedInformativeDistances(a: List<Long>, b: List<Long>): List<Int> {
        val n = minOf(a.size, b.size)
        if (n == 0) return emptyList()
        return buildList(n) {
            for (i in 0 until n) {
                if (isInformative(a[i]) && isInformative(b[i])) {
                    add(java.lang.Long.bitCount(a[i] xor b[i]))
                }
            }
        }
    }

    /** Flat/near-flat frames and envelopes are collision magnets and never count as evidence. */
    fun isInformative(hash: Long): Boolean = java.lang.Long.bitCount(hash) in 4..60

    private fun durationsCompatible(a: Long?, b: Long?): Boolean {
        if (a == null || b == null || a <= 0L || b <= 0L) return false
        val allowed = max(DURATION_ABSOLUTE_TOLERANCE_MS, (max(a, b) * DURATION_RELATIVE_TOLERANCE).toLong())
        return abs(a - b) <= allowed
    }

    private const val MIN_VIDEO_EVIDENCE_FRAMES = 3
    private const val VIDEO_FRAME_CLOSE_THRESHOLD = 6
    private const val VIDEO_MEAN_THRESHOLD = 5.0
    private const val VIDEO_MAX_FRAME_THRESHOLD = 10
    private const val PDF_MEAN_THRESHOLD = 4.0
    private const val PDF_MAX_PAGE_THRESHOLD = 7
    private const val AUDIO_ENVELOPE_THRESHOLD = 4
    private const val DURATION_ABSOLUTE_TOLERANCE_MS = 2_000L
    private const val DURATION_RELATIVE_TOLERANCE = 0.03
}
