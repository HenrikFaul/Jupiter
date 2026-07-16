package com.jupiter.filemanager.data.index

import kotlin.math.abs

/**
 * Compact, allocation-light codecs for persistent duplicate/index metadata.
 *
 * SQLite TEXT stores two characters for every digest byte. The index only needs the raw
 * comparison evidence, so production SHA-1 values and ordered 64-bit media signatures are stored
 * as BLOBs. Legacy strings remain readable during the rolling v9 -> v10 transition.
 */
object CompactMetadataCodec {

    const val SHA1_BYTES = 20
    private const val MAX_VECTOR_HASHES = 16
    private const val MAX_DIMENSION = 1_000_000
    private const val ASPECT_TOLERANCE_PER_MILLE = 80L
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    /** Returns the raw 20-byte SHA-1 represented by [value], or null for a legacy/test token. */
    fun sha1ToBytes(value: String?): ByteArray? {
        if (value?.length != SHA1_BYTES * 2) return null
        return hexToBytes(value)
    }

    /** Extracts the SHA-1 suffix from the legacy `size:hex` quick-hash representation. */
    fun legacyQuickHashToBytes(value: String?): ByteArray? =
        sha1ToBytes(value?.substringAfterLast(':'))

    fun hexToBytes(value: String?): ByteArray? {
        if (value.isNullOrEmpty() || value.length % 2 != 0) return null
        val out = ByteArray(value.length / 2)
        for (i in out.indices) {
            val high = value[i * 2].digitToIntOrNull(16) ?: return null
            val low = value[i * 2 + 1].digitToIntOrNull(16) ?: return null
            out[i] = ((high shl 4) or low).toByte()
        }
        return out
    }

    fun bytesToHex(value: ByteArray): String {
        val out = CharArray(value.size * 2)
        var offset = 0
        for (byte in value) {
            val unsigned = byte.toInt() and 0xFF
            out[offset++] = HEX_DIGITS[unsigned ushr 4]
            out[offset++] = HEX_DIGITS[unsigned and 0x0F]
        }
        return String(out)
    }

    /** Encodes ordered 64-bit samples directly; count is recovered from BLOB length. */
    fun encodeLongVector(values: List<Long>): ByteArray {
        require(values.isNotEmpty() && values.size <= MAX_VECTOR_HASHES)
        val out = ByteArray(values.size * Long.SIZE_BYTES)
        values.forEachIndexed { index, value ->
            val base = index * Long.SIZE_BYTES
            for (byteIndex in 0 until Long.SIZE_BYTES) {
                out[base + byteIndex] = (value ushr (56 - byteIndex * 8)).toByte()
            }
        }
        return out
    }

    fun decodeLongVector(value: ByteArray?): List<Long>? {
        if (value == null || value.isEmpty() || value.size % Long.SIZE_BYTES != 0) return null
        val count = value.size / Long.SIZE_BYTES
        if (count > MAX_VECTOR_HASHES) return null
        return List(count) { index ->
            val base = index * Long.SIZE_BYTES
            var decoded = 0L
            for (byteIndex in 0 until Long.SIZE_BYTES) {
                decoded = (decoded shl 8) or (value[base + byteIndex].toLong() and 0xFFL)
            }
            decoded
        }
    }

    /** Converts the old comma-separated unsigned-hex long vector without decoding media again. */
    fun legacyLongVectorToBlob(value: String?): ByteArray? {
        if (value.isNullOrBlank()) return null
        val hashes = value.split(',').map { token ->
            if (token.length != 16) return null
            runCatching { token.toULong(16).toLong() }.getOrNull() ?: return null
        }
        return hashes.takeIf { it.isNotEmpty() && it.size <= MAX_VECTOR_HASHES }
            ?.let(::encodeLongVector)
    }

    /** Packs width and height into one SQLite INTEGER (8 bytes, no extra metadata table). */
    fun packDimensions(width: Int, height: Int): Long? {
        if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION) return null
        return (width.toLong() shl 32) or (height.toLong() and 0xFFFF_FFFFL)
    }

    fun unpackDimensions(value: Long?): Pair<Int, Int>? {
        value ?: return null
        val width = (value ushr 32).toInt()
        val height = value.toInt()
        return if (width in 1..MAX_DIMENSION && height in 1..MAX_DIMENSION) {
            width to height
        } else {
            null
        }
    }

    /**
     * Orientation-independent aspect-ratio veto. Missing metadata stays compatible so a rolling
     * backfill cannot hide a real duplicate; clearly different shapes cannot form a review group.
     */
    fun dimensionsCompatible(a: Long?, b: Long?): Boolean {
        val (aw, ah) = unpackDimensions(a) ?: return true
        val (bw, bh) = unpackDimensions(b) ?: return true
        val aLong = maxOf(aw, ah).toLong()
        val aShort = minOf(aw, ah).toLong()
        val bLong = maxOf(bw, bh).toLong()
        val bShort = minOf(bw, bh).toLong()
        val left = aLong * bShort
        val right = bLong * aShort
        val scale = maxOf(left, right)
        return abs(left - right) * 1_000L <= scale * ASPECT_TOLERANCE_PER_MILLE
    }
}
