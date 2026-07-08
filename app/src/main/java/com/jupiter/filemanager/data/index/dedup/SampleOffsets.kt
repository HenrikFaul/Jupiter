package com.jupiter.filemanager.data.index.dedup

/**
 * Pure computation of the byte ranges to sample for the chunk/sample-fingerprint layer (§4.4):
 * a fast approximation of identity for large opaque binaries without reading the whole file.
 *
 * Samples first + last + middle + a few size-seeded pseudo-random interior positions, so the SAME
 * file always samples the SAME positions (a fingerprint that is comparable across files). For a
 * file that fits within a few chunks, the "sample" IS the whole file (sampling buys nothing).
 * This layer is a candidate PREFILTER, never a final decision — matching samples with a differing
 * total size is a veto, not a confirmation.
 */
object SampleOffsets {

    /**
     * @param size total file size in bytes.
     * @param chunk sample window size in bytes.
     * @param interior how many extra size-seeded interior samples to add (0–8).
     * @return non-overlapping, ascending byte ranges within [0, size). Empty when size <= 0.
     */
    fun compute(size: Long, chunk: Long, interior: Int = 4): List<LongRange> {
        if (size <= 0L || chunk <= 0L) return emptyList()
        // Small files: one range covering everything.
        if (size <= chunk * 3) return listOf(0L until size)

        val positions = sortedSetOf(0L, size - chunk, (size / 2) - (chunk / 2))
        // Deterministic interior positions seeded by size (same file → same offsets).
        var seed = size * 0x9E3779B97F4A7C15uL.toLong()
        val span = size - chunk
        repeat(interior.coerceIn(0, 8)) {
            seed = seed * 6364136223846793005L + 1442695040888963407L // LCG step
            val pos = (Math.floorMod(seed, span))
            positions.add(pos)
        }
        // Convert to non-overlapping ranges (merge windows that touch/overlap after sorting).
        val ranges = ArrayList<LongRange>()
        for (startRaw in positions) {
            val start = startRaw.coerceIn(0L, size - chunk)
            val end = (start + chunk).coerceAtMost(size)
            if (ranges.isNotEmpty() && start <= ranges.last().last) {
                val prev = ranges.removeAt(ranges.size - 1)
                ranges.add(prev.first until maxOf(prev.last + 1, end))
            } else {
                ranges.add(start until end)
            }
        }
        return ranges
    }
}
