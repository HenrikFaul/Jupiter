package com.jupiter.filemanager.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Performs the actual media compression once the UI has picked a source
 * [FileItem] and a target [CompressPreset].
 *
 * Two independent paths:
 *  - **Images** are decoded with [BitmapFactory] (down-sampled first, then
 *    scaled to the exact target long edge) and re-encoded to JPEG/WEBP.
 *  - **Videos** are transcoded with a Media3 [Transformer]. Transformer must be
 *    created and started on a thread with a [android.os.Looper], so the whole
 *    build/start/cancel lifecycle runs on [Dispatchers.Main] and completion is
 *    bridged back to the caller through a [CompletableDeferred]. Progress is
 *    polled off a [ProgressHolder] on a lightweight timer.
 *
 * Every decode / JNI / encode call is individually guarded. A bad, corrupt or
 * unsupported file can never crash the app — it degrades to an
 * [AppResult.Failure]. Coroutine cancellation is honoured (the running
 * transform is cancelled and its partial output removed).
 */
@Singleton
@OptIn(UnstableApi::class)
class MediaCompressor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    private companion object {
        /** Hard ceiling for any single blocking JNI / decode probe call. */
        const val PROBE_TIMEOUT_MS = 4_000L

        /** JPEG/WEBP quality used when re-encoding images. */
        const val IMAGE_QUALITY = 80

        /** How often video progress is sampled while a transform runs. */
        const val PROGRESS_POLL_MS = 250L
    }

    // =====================================================================
    // Dimensions
    // =====================================================================

    /**
     * Reads the intrinsic pixel dimensions of [item] (orientation-corrected),
     * or `null` when the file is not decodable / not a supported media type.
     */
    suspend fun dimensionsOf(item: FileItem): MediaDimensions? = withContext(dispatcher) {
        when (item.type) {
            FileType.IMAGE -> imageDimensions(item.path)
            FileType.VIDEO -> videoDimensions(item.path)
            else -> null
        }
    }

    private fun imageDimensions(path: String): MediaDimensions? {
        var width = 0
        var height = 0
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            width = bounds.outWidth
            height = bounds.outHeight
        } catch (_: Throwable) {
            // keep zeros
        }

        // Rotated orientations swap the visible width/height.
        try {
            val exif = ExifInterface(path)
            when (
                exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_TRANSVERSE,
                -> {
                    val tmp = width
                    width = height
                    height = tmp
                }
            }
        } catch (_: Throwable) {
            // orientation is best-effort
        }

        return if (width > 0 && height > 0) MediaDimensions(width, height) else null
    }

    private suspend fun videoDimensions(path: String): MediaDimensions? {
        var width = 0
        var height = 0
        var rotation = 0
        withRetriever(path) { retriever ->
            width = readInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            height = readInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            rotation = readInt(retriever, MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        }
        if (width <= 0 || height <= 0) return null
        // 90°/270° rotation swaps the displayed dimensions.
        return if (rotation == 90 || rotation == 270) {
            MediaDimensions(height, width)
        } else {
            MediaDimensions(width, height)
        }
    }

    // =====================================================================
    // Image compression
    // =====================================================================

    /**
     * Down-scales [item] so its long edge is at most [CompressPreset.targetLongEdgePx]
     * (never upscaling) and re-encodes it to [outFile]. Returns the size of the
     * written file on success.
     */
    suspend fun compressImage(
        item: FileItem,
        preset: CompressPreset,
        outFile: File,
    ): AppResult<Long> = withContext(dispatcher) {
        var decoded: Bitmap? = null
        var oriented: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val target = preset.targetLongEdgePx.takeIf { it > 0 } ?: return@withContext failure(
                "Invalid target resolution.",
            )

            // 1. Bounds pass to size the down-sample factor.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(item.path, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) {
                return@withContext failure("Could not read image \"${item.name}\".")
            }

            // 2. Decode down-sampled (power-of-two) to keep memory bounded.
            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeInSampleSize(max(srcW, srcH), target)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            decoded = BitmapFactory.decodeFile(item.path, opts)
                ?: return@withContext failure("Could not decode image \"${item.name}\".")

            // 3. Apply EXIF rotation so the saved pixels match what the user sees.
            oriented = applyExifOrientation(item.path, decoded)

            // 4. Exact scale so the long edge <= target (never upscales).
            scaled = scaleToLongEdge(oriented, target)

            // 5. Re-encode.
            val format = chooseImageFormat(outFile, item, scaled.hasAlpha())
            outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(outFile).use { out ->
                val ok = scaled.compress(format, IMAGE_QUALITY, out)
                out.flush()
                if (!ok) {
                    return@withContext failure("Could not encode image \"${item.name}\".")
                }
            }

            val size = safeLength(outFile)
            if (size <= 0L) {
                return@withContext failure("Compression produced an empty file.")
            }
            AppResult.Success(size)
        } catch (t: Throwable) {
            safeDelete(outFile)
            failure("Image compression failed: ${t.message ?: t.javaClass.simpleName}", t)
        } finally {
            // Recycle every distinct bitmap we created.
            recycleDistinct(decoded, oriented, scaled)
        }
    }

    private fun computeInSampleSize(longEdge: Int, target: Int): Int {
        if (target <= 0 || longEdge <= 0) return 1
        var sample = 1
        // Largest power of two that still leaves us at or above the target.
        while (longEdge / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    private fun applyExifOrientation(path: String, bitmap: Bitmap): Bitmap {
        val matrix = try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
            matrixForOrientation(orientation)
        } catch (_: Throwable) {
            null
        } ?: return bitmap

        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun matrixForOrientation(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f); matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f); matrix.postScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    private fun scaleToLongEdge(src: Bitmap, target: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= target || longEdge <= 0) return src // never upscale
        val scale = target.toDouble() / longEdge
        val w = max(1, (src.width * scale).roundToInt())
        val h = max(1, (src.height * scale).roundToInt())
        return try {
            Bitmap.createScaledBitmap(src, w, h, true)
        } catch (_: Throwable) {
            src
        }
    }

    @Suppress("DEPRECATION")
    private fun chooseImageFormat(
        outFile: File,
        item: FileItem,
        hasAlpha: Boolean,
    ): Bitmap.CompressFormat {
        val ext = outFile.extension.ifEmpty { item.extension }.lowercase()
        val webp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
        return when (ext) {
            "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
            "webp" -> webp
            // JPEG cannot store alpha; fall back to (lossy) WEBP to keep transparency.
            "png" -> if (hasAlpha) webp else Bitmap.CompressFormat.JPEG
            else -> if (hasAlpha) webp else Bitmap.CompressFormat.JPEG
        }
    }

    // =====================================================================
    // Video compression (Media3 Transformer)
    // =====================================================================

    /**
     * Transcodes [item] to [outFile] at the resolution (and best-effort bitrate)
     * described by [preset]. [onProgress] is invoked with 0..100 percentages as
     * the transform runs. Returns the output file size on success.
     *
     * Runs entirely on [Dispatchers.Main] because Transformer requires a Looper
     * thread. Coroutine cancellation cancels the transform and cleans up.
     */
    suspend fun compressVideo(
        item: FileItem,
        preset: CompressPreset,
        outFile: File,
        onProgress: (Int) -> Unit,
    ): AppResult<Long> {
        // Probing runs off-main; the transform itself must be on Main.
        val sourceDims = dimensionsOf(item)
        val target = presentationBox(sourceDims, preset.targetLongEdgePx)

        return withContext(Dispatchers.Main) {
            // Transformer refuses to overwrite; start from a clean output path.
            safeDelete(outFile)
            outFile.parentFile?.let { if (!it.exists()) it.mkdirs() }

            try {
                coroutineScope {
                    val completion = CompletableDeferred<AppResult<Long>>()

                    val transformer = buildTransformer(preset, completion, outFile)
                    val edited = buildEditedItem(item, target)

                    try {
                        transformer.start(edited, outFile.absolutePath)
                    } catch (t: Throwable) {
                        safeDelete(outFile)
                        return@coroutineScope failure(
                            "Could not start video compression: " +
                                (t.message ?: t.javaClass.simpleName),
                            t,
                        )
                    }

                    // Concurrent progress poller. On the single-threaded Main
                    // dispatcher it runs while we suspend on completion.await().
                    val poller = launch {
                        val holder = ProgressHolder()
                        while (isActive) {
                            val state = try {
                                transformer.getProgress(holder)
                            } catch (_: Throwable) {
                                Transformer.PROGRESS_STATE_NOT_STARTED
                            }
                            if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress(holder.progress.coerceIn(0, 100))
                            }
                            delay(PROGRESS_POLL_MS)
                        }
                    }

                    try {
                        val result = completion.await()
                        poller.cancel()
                        if (result is AppResult.Success) onProgress(100) else safeDelete(outFile)
                        result
                    } catch (ce: CancellationException) {
                        poller.cancel()
                        try {
                            transformer.cancel()
                        } catch (_: Throwable) {
                            // best-effort
                        }
                        safeDelete(outFile)
                        throw ce
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                safeDelete(outFile)
                failure(
                    "Video compression failed: ${t.message ?: t.javaClass.simpleName}",
                    t,
                )
            }
        }
    }

    /** Builds a Transformer wired to resume [completion] on finish/error. */
    private fun buildTransformer(
        preset: CompressPreset,
        completion: CompletableDeferred<AppResult<Long>>,
        outFile: File,
    ): Transformer {
        val builder = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    val size = exportResult.fileSizeBytes.takeIf { it > 0L } ?: safeLength(outFile)
                    completion.complete(AppResult.Success(size))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    completion.complete(
                        AppResult.Failure(
                            AppError.Io(
                                "Video compression failed: " +
                                    (exportException.message ?: "encoder error"),
                                exportException,
                            ),
                        ),
                    )
                }
            })

        // Best-effort target bitrate. If the encoder factory API misbehaves we
        // silently fall back to resolution-only compression (still large wins).
        if (preset.bitrateBps > 0) {
            try {
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(preset.bitrateBps)
                            .build(),
                    )
                    .build()
                builder.setEncoderFactory(encoderFactory)
            } catch (_: Throwable) {
                // resolution-only fallback
            }
        }

        return builder.build()
    }

    private fun buildEditedItem(item: FileItem, target: Pair<Int, Int>?): EditedMediaItem {
        val uri = Uri.fromFile(File(item.path))
        val mediaItem = MediaItem.fromUri(uri)
        val builder = EditedMediaItem.Builder(mediaItem)
        if (target != null) {
            val (w, h) = target
            builder.setEffects(
                Effects(
                    /* audioProcessors = */ emptyList(),
                    /* videoEffects = */ listOf(
                        Presentation.createForWidthAndHeight(
                            w,
                            h,
                            Presentation.LAYOUT_SCALE_TO_FIT,
                        ),
                    ),
                ),
            )
        }
        return builder.build()
    }

    /**
     * Computes the target bounding box for [Presentation], or `null` when the
     * source is already at/below target (so we transcode without resizing and
     * never upscale). Dimensions are forced even for encoder friendliness.
     */
    private fun presentationBox(src: MediaDimensions?, targetLongEdge: Int): Pair<Int, Int>? {
        if (targetLongEdge <= 0) return null
        if (src == null || src.width <= 0 || src.height <= 0) {
            // Unknown source: SCALE_TO_FIT within a square box caps the long edge.
            val e = makeEven(targetLongEdge)
            return e to e
        }
        val longEdge = max(src.width, src.height)
        if (longEdge <= targetLongEdge) return null // already small enough
        val scale = targetLongEdge.toDouble() / longEdge
        val w = makeEven((src.width * scale).roundToInt())
        val h = makeEven((src.height * scale).roundToInt())
        return w to h
    }

    private fun makeEven(value: Int): Int {
        val v = max(2, value)
        return if (v % 2 == 0) v else v - 1
    }

    // =====================================================================
    // Shared helpers
    // =====================================================================

    private fun failure(message: String, cause: Throwable? = null): AppResult<Long> =
        AppResult.Failure(AppError.Io(message, cause))

    private fun safeLength(file: File): Long =
        try {
            file.length()
        } catch (_: Throwable) {
            0L
        }

    private fun safeDelete(file: File) {
        try {
            if (file.exists()) file.delete()
        } catch (_: Throwable) {
            // best-effort
        }
    }

    private fun recycleDistinct(vararg bitmaps: Bitmap?) {
        val seen = HashSet<Bitmap>()
        for (bitmap in bitmaps) {
            if (bitmap == null || bitmap.isRecycled) continue
            if (seen.add(bitmap)) {
                try {
                    bitmap.recycle()
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }

    private suspend fun withRetriever(path: String, block: (MediaMetadataRetriever) -> Unit) {
        val retriever = MediaMetadataRetriever()
        try {
            withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                try {
                    retriever.setDataSource(path)
                    block(retriever)
                } catch (_: Throwable) {
                    // leave caller defaults
                }
            }
        } finally {
            try {
                retriever.release()
            } catch (_: Throwable) {
                // ignore
            }
        }
    }

    private fun readInt(retriever: MediaMetadataRetriever, key: Int): Int =
        try {
            retriever.extractMetadata(key)?.trim()?.toIntOrNull() ?: 0
        } catch (_: Throwable) {
            0
        }
}
