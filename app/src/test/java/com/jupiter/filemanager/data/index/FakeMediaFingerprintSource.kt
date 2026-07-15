package com.jupiter.filemanager.data.index

/**
 * Test double for [MediaFingerprintSource]. The real decoders need on-device codecs
 * (`MediaMetadataRetriever` / `PdfRenderer` / `MediaCodec`) that can't run under the JVM/Robolectric
 * CI, so tests script the fingerprints per path here and exercise the arrival-pipeline WIRING
 * (detector branches, per-type comparison, alerts, backfill) against them. The decode itself is
 * verified on a device.
 *
 * A path absent from a map yields [StructuralHash.UNHASHABLE] (decoded-but-not-comparable), matching
 * the real contract; put a path into [nullPaths] to simulate a transient failure (null).
 */
class FakeMediaFingerprintSource(
    private val video: Map<String, Long> = emptyMap(),
    private val pdf: Map<String, Long> = emptyMap(),
    private val audio: Map<String, Long> = emptyMap(),
    private val nullPaths: Set<String> = emptySet(),
) : MediaFingerprintSource {

    override fun videoKeyframeHash(path: String): Long? = lookup(path, video)
    override fun pdfRenderHash(path: String): Long? = lookup(path, pdf)
    override fun audioAcousticHash(path: String): Long? = lookup(path, audio)

    override fun videoFingerprint(path: String): MediaFingerprint? =
        lookup(path, video)?.let { hash ->
            if (hash == StructuralHash.UNHASHABLE) MediaFingerprint.single(hash)
            else MediaFingerprint(List(5) { hash }, extent = 10_000L)
        }

    override fun pdfFingerprint(path: String): MediaFingerprint? =
        lookup(path, pdf)?.let { hash ->
            if (hash == StructuralHash.UNHASHABLE) MediaFingerprint.single(hash)
            else MediaFingerprint(List(3) { hash }, extent = 3L)
        }

    override fun audioFingerprint(path: String): MediaFingerprint? =
        lookup(path, audio)?.let { hash ->
            if (hash == StructuralHash.UNHASHABLE) MediaFingerprint.single(hash)
            else MediaFingerprint(listOf(hash), extent = 10_000L)
        }

    private fun lookup(path: String, map: Map<String, Long>): Long? = when {
        path in nullPaths -> null
        else -> map[path] ?: StructuralHash.UNHASHABLE
    }
}
