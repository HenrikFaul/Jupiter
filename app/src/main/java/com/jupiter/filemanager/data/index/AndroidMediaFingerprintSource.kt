package com.jupiter.filemanager.data.index

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Real, on-device implementation of [MediaFingerprintSource] using the platform media stack:
 * `MediaMetadataRetriever` (video frames), `PdfRenderer` (PDF pages), and `MediaExtractor` +
 * `MediaCodec` (audio PCM). These require genuine codecs, so this class's decode paths are verified
 * on a device — the JVM/Robolectric CI proves the surrounding pipeline against a fake instead.
 *
 * Every method is total: it returns a 64-bit fingerprint on success, [StructuralHash.UNHASHABLE]
 * when the file exists but is not decodable as that media type (so it is marked once and never
 * retried or matched), and `null` only for a transient failure. All platform handles are closed in
 * `finally`, and nothing is decoded on the main thread (callers run on the IO dispatcher).
 */
@Singleton
class AndroidMediaFingerprintSource @Inject constructor() : MediaFingerprintSource {

    override fun videoKeyframeHash(path: String): Long? {
        val file = File(path)
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            // A representative frame near the middle is far more distinctive than the first frame
            // (title cards / black lead-ins collide); OPTION_CLOSEST_SYNC keeps it cheap.
            val midUs = if (durationMs > 0) durationMs * 1000L / 2 else -1L
            val frame: Bitmap = (
                if (midUs >= 0) {
                    retriever.getFrameAtTime(midUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } else {
                    retriever.frameAtTime
                }
                ) ?: return StructuralHash.UNHASHABLE
            val hash = BitmapDHash.of(frame)
            frame.recycle()
            hash
        } catch (_: Exception) {
            // setDataSource throws for a non-media / unsupported container → conservative UNHASHABLE.
            StructuralHash.UNHASHABLE
        } finally {
            runCatching { retriever.release() }
        }
    }

    override fun pdfRenderHash(path: String): Long? {
        val file = File(path)
        if (!file.isFile) return null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return StructuralHash.UNHASHABLE
            // PdfRenderer.Page has close() but does not implement Closeable → close explicitly.
            val page = renderer.openPage(0)
            try {
                // Cap the render so a huge page can't blow up memory; keep the page aspect ratio.
                val scale = minOf(
                    RENDER_MAX_DIMEN.toFloat() / page.width,
                    RENDER_MAX_DIMEN.toFloat() / page.height,
                    1f,
                )
                val w = maxOf(1, (page.width * scale).toInt())
                val h = maxOf(1, (page.height * scale).toInt())
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // PdfRenderer draws transparent where the page has no content; a white backing
                // keeps the luminance structure the same as a viewer would show.
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val hash = BitmapDHash.of(bitmap)
                bitmap.recycle()
                hash
            } finally {
                runCatching { page.close() }
            }
        } catch (_: Exception) {
            StructuralHash.UNHASHABLE // not a readable PDF (or encrypted) → conservative
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
        }
    }

    override fun audioAcousticHash(path: String): Long? {
        val file = File(path)
        if (!file.isFile) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(path)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return StructuralHash.UNHASHABLE
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return StructuralHash.UNHASHABLE
            val channels = runCatching { format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }
                .getOrDefault(1).coerceAtLeast(1)

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val energies = decodeEnergyEnvelope(extractor, codec, channels)
            if (energies == null || energies.size < WINDOWS) return StructuralHash.UNHASHABLE
            envelopeHash(energies)
        } catch (_: Exception) {
            StructuralHash.UNHASHABLE
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    /**
     * Decodes up to [MAX_DECODE_SAMPLES] mono PCM samples and returns the summed energy of each of
     * [WINDOWS] equal, contiguous windows — a coarse loudness envelope robust to format/bitrate/
     * volume changes. Returns null if too little audio was decoded.
     */
    private fun decodeEnergyEnvelope(
        extractor: MediaExtractor,
        codec: MediaCodec,
        channels: Int,
    ): DoubleArray? {
        val samples = ArrayList<Double>(min(MAX_DECODE_SAMPLES, 1 shl 16))
        val info = MediaCodec.BufferInfo()
        var sawInputEos = false
        var sawOutputEos = false
        // Guard against a codec that neither progresses nor signals EOS: bound the number of
        // consecutive no-output polls so a pathological decoder can't spin forever.
        var idlePolls = 0

        while (!sawOutputEos && samples.size < MAX_DECODE_SAMPLES && idlePolls < MAX_IDLE_POLLS) {
            if (!sawInputEos) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val inBuf = codec.getInputBuffer(inIndex)
                    val read = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                    if (read < 0) {
                        codec.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                        )
                        sawInputEos = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, read, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex >= 0) {
                idlePolls = 0
                val outBuf = codec.getOutputBuffer(outIndex)
                if (outBuf != null && info.size > 0) {
                    appendMonoSamples(outBuf, info, channels, samples)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
            } else {
                idlePolls++
            }
        }
        if (samples.size < WINDOWS) return null

        val energies = DoubleArray(WINDOWS)
        val per = samples.size / WINDOWS
        for (w in 0 until WINDOWS) {
            var sum = 0.0
            val start = w * per
            for (i in start until start + per) {
                val s = samples[i]
                sum += s * s
            }
            energies[w] = sum
        }
        return energies
    }

    /** Reads 16-bit PCM from [buffer], down-mixes to mono, and appends to [out] (normalized). */
    private fun appendMonoSamples(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        channels: Int,
        out: ArrayList<Double>,
    ) {
        buffer.position(info.offset)
        buffer.limit(info.offset + info.size)
        val shorts = buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val frames = shorts.remaining() / channels
        var i = 0
        while (i < frames && out.size < MAX_DECODE_SAMPLES) {
            var acc = 0
            for (c in 0 until channels) acc += shorts.get(i * channels + c).toInt()
            out.add(acc.toDouble() / (channels * Short.MAX_VALUE))
            i++
        }
    }

    /**
     * Reduces a loudness envelope to a 64-bit fingerprint: bit w is set when window w is at least as
     * loud as the previous window (wrap-around for w=0). Relative comparisons make it invariant to
     * overall volume/gain, and re-encoding preserves the loudness contour, so a re-compressed copy
     * of the same track lands at a small Hamming distance.
     */
    private fun envelopeHash(energies: DoubleArray): Long {
        var hash = 0L
        for (w in 0 until WINDOWS) {
            val prev = if (w == 0) energies[WINDOWS - 1] else energies[w - 1]
            if (energies[w] >= prev) hash = hash or (1L shl w)
        }
        // Never collide with the UNHASHABLE sentinel for a real fingerprint.
        return if (hash == StructuralHash.UNHASHABLE) hash + 1 else hash
    }

    private companion object {
        /** Longest edge (px) of a rendered PDF page before the 9×8 dHash reduction. */
        const val RENDER_MAX_DIMEN = 320

        /** 64 loudness windows → one bit each → a 64-bit envelope fingerprint. */
        const val WINDOWS = 64

        /** ~12 s at 44.1 kHz mono — enough for a stable envelope, bounded memory. */
        const val MAX_DECODE_SAMPLES = 44_100 * 12

        const val TIMEOUT_US = 10_000L

        /** ~5 s of consecutive empty 10 ms polls → give up (codec stalled). */
        const val MAX_IDLE_POLLS = 500
    }
}
