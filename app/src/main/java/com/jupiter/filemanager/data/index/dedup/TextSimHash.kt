package com.jupiter.filemanager.data.index.dedup

/**
 * Pure 64-bit SimHash for near-duplicate TEXT and CODE detection — the semantic-layer signal for
 * those types. SimHash maps a token multiset to a 64-bit fingerprint such that documents with
 * mostly-overlapping tokens land at a small Hamming distance, so it is stable under reformatting,
 * whitespace/case changes, and small edits (unlike a content hash, which any edit shatters).
 *
 * Normalization (formatting-insensitive): lowercased, split on non-alphanumeric runs, empty
 * tokens dropped. Each token is weighted by frequency. Comments are NOT stripped here (a cheap,
 * language-agnostic baseline); a language-aware tokenizer is a future refinement.
 */
object TextSimHash {

    /** Near-duplicate threshold: Hamming distance ≤ this (of 64) counts as the same text. */
    const val DEFAULT_NEAR_THRESHOLD = 3

    /** Sentinel for text that produced no tokens (empty/binary) — never matches anything. */
    const val EMPTY = 0L.inv() // all bits set is astronomically unlikely for real text; treat as sentinel

    /** Computes the 64-bit SimHash of [text]. Returns [EMPTY] when there are no tokens. */
    fun of(text: String): Long {
        val counts = HashMap<String, Int>()
        var start = -1
        val n = text.length
        // Manual tokenizer: alphanumeric runs, lowercased — avoids regex allocation on large files.
        var i = 0
        while (i <= n) {
            val c = if (i < n) text[i] else ' '
            val alnum = c.isLetterOrDigit()
            if (alnum && start < 0) {
                start = i
            } else if (!alnum && start >= 0) {
                val token = text.substring(start, i).lowercase()
                counts[token] = (counts[token] ?: 0) + 1
                start = -1
            }
            i++
        }
        if (counts.isEmpty()) return EMPTY

        val bitSums = IntArray(64)
        for ((token, weight) in counts) {
            val h = token64(token)
            var bit = 0
            while (bit < 64) {
                if ((h ushr bit) and 1L == 1L) bitSums[bit] += weight else bitSums[bit] -= weight
                bit++
            }
        }
        var result = 0L
        var bit = 0
        while (bit < 64) {
            if (bitSums[bit] > 0) result = result or (1L shl bit)
            bit++
        }
        return result
    }

    /** Hamming distance between two SimHashes (0 = identical token profile). */
    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Normalized similarity in [0,1] for the fusion engine (1 = identical, 0.5 = unrelated). */
    fun similarity(a: Long, b: Long): Double =
        if (a == EMPTY || b == EMPTY) 0.0 else 1.0 - distance(a, b) / 64.0

    /** True when [a] and [b] are near-duplicate texts. */
    fun isNear(a: Long, b: Long, threshold: Int = DEFAULT_NEAR_THRESHOLD): Boolean =
        a != EMPTY && b != EMPTY && distance(a, b) <= threshold

    /** Stable 64-bit hash of a token (FNV-1a, no external deps). */
    private fun token64(token: String): Long {
        var h = 0xcbf29ce484222325uL.toLong() // FNV-1a 64-bit offset basis
        for (ch in token) {
            h = h xor ch.code.toLong()
            h *= 0x100000001b3L // FNV-1a 64-bit prime (fits in Long)
        }
        return h
    }
}
