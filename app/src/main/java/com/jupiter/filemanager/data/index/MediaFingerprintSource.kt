package com.jupiter.filemanager.data.index

/**
 * Seam over the on-device media decoders that produce near-duplicate fingerprints for video, PDF,
 * and audio. Extracted behind an interface for two reasons:
 *
 *  1. **Testability.** The real implementation drives `MediaMetadataRetriever` / `PdfRenderer` /
 *     `MediaCodec`, which need genuine platform codecs and cannot run under the JVM/Robolectric CI.
 *     The arrival-pipeline wiring (detector branches, per-type comparison, alerts, backfill) is
 *     proven in CI against a scripted fake; the decode itself is verified on a device.
 *  2. **Isolation.** Media decode is failure-prone (DRM, unsupported codecs, corrupt containers);
 *     confining it behind one contract keeps those failures from leaking into the index pipeline.
 *
 * Every method returns a 64-bit fingerprint stored in [FileIndexEntry.structuralHash] and compared
 * ONLY within the same file type. Contract, identical to [PerceptualHashSource] / [StructuralFingerprintSource]:
 *  - a real fingerprint on success;
 *  - [StructuralHash.UNHASHABLE] when the file exists but cannot be decoded (marked once, never
 *    retried, never matched);
 *  - `null` only for a transient failure worth retrying on a later pass.
 */
interface MediaFingerprintSource {

    /** Perceptual fingerprint of a representative video frame (dHash), for re-encode near-dups. */
    fun videoKeyframeHash(path: String): Long?

    /** Perceptual fingerprint of a rendered PDF page (dHash), for scanned/exported near-dups. */
    fun pdfRenderHash(path: String): Long?

    /** Coarse acoustic fingerprint (energy-envelope hash), for re-encoded audio near-dups. */
    fun audioAcousticHash(path: String): Long?

    /**
     * Multi-sample VIDEO descriptor. Implementations should sample the same normalized timeline
     * positions for every file; the default keeps test/third-party implementations source
     * compatible but production overrides it with a real temporal signature.
     */
    fun videoFingerprint(path: String): MediaFingerprint? =
        videoKeyframeHash(path)?.let { MediaFingerprint.single(it) }

    /** Multi-page PDF descriptor (first/middle/last page in the Android implementation). */
    fun pdfFingerprint(path: String): MediaFingerprint? =
        pdfRenderHash(path)?.let { MediaFingerprint.single(it) }

    /** Acoustic descriptor plus recording duration. */
    fun audioFingerprint(path: String): MediaFingerprint? =
        audioAcousticHash(path)?.let { MediaFingerprint.single(it) }
}

/**
 * Persistable, type-aware media descriptor. [hashes] is an ordered temporal/page signature and
 * [extent] is duration in milliseconds (video/audio) or page count (PDF). A descriptor version is
 * stored with every row so an algorithm upgrade can fail closed instead of comparing incompatible
 * fingerprints.
 */
data class MediaFingerprint(
    val hashes: List<Long>,
    val extent: Long? = null,
    val version: Int = CURRENT_VERSION,
    val width: Int = 0,
    val height: Int = 0,
) {
    val primaryHash: Long get() = hashes.firstOrNull() ?: StructuralHash.UNHASHABLE

    /** Legacy v2 TEXT representation; new writes use [encodeCompact]. */
    fun encode(): String = hashes.joinToString(",") { it.toULong().toString(16).padStart(16, '0') }

    /** Minimal persistent representation: exactly eight bytes per ordered sample. */
    fun encodeCompact(): ByteArray = CompactMetadataCodec.encodeLongVector(hashes)

    val visualGeometry: Long?
        get() = CompactMetadataCodec.packDimensions(width, height)

    companion object {
        /** v2 replaces the unsafe single-frame/single-page media descriptors. */
        const val CURRENT_VERSION = 2

        fun single(hash: Long, extent: Long? = null): MediaFingerprint =
            MediaFingerprint(listOf(hash), extent)

        fun decode(encoded: String?, extent: Long?, version: Int): MediaFingerprint? {
            if (encoded.isNullOrBlank() || version != CURRENT_VERSION) return null
            val hashes = encoded.split(',').mapNotNull { token ->
                runCatching { token.toULong(16).toLong() }.getOrNull()
            }
            return hashes.takeIf { it.isNotEmpty() }?.let { MediaFingerprint(it, extent, version) }
        }

        /** Reads compact v10 metadata first, then the retained v9 TEXT fallback. */
        fun decode(
            compact: ByteArray?,
            legacy: String?,
            extent: Long?,
            version: Int,
            visualGeometry: Long?,
        ): MediaFingerprint? {
            if (version != CURRENT_VERSION) return null
            val hashes = CompactMetadataCodec.decodeLongVector(compact)
                ?: decode(legacy, extent, version)?.hashes
                ?: return null
            val dimensions = CompactMetadataCodec.unpackDimensions(visualGeometry)
            return MediaFingerprint(
                hashes = hashes,
                extent = extent,
                version = version,
                width = dimensions?.first ?: 0,
                height = dimensions?.second ?: 0,
            )
        }
    }
}
