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
}
