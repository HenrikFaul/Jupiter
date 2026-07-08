package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Robolectric + in-memory Room proof of the reported perceptual-dedup bug: the SAME picture,
 * saved once as the on-device original (indexed with METADATA ONLY — no perceptual fingerprint,
 * the realistic post-survey / pre-backfill state) and once as a freshly-downloaded copy in a
 * different format AND resolution, is now flagged SIMILAR the moment the copy arrives.
 *
 * Before the fix, [FileIndexRepository.findNearDuplicateImages] only compared against rows that
 * were ALREADY fingerprinted, so an un-backfilled original was invisible and no alert ever fired —
 * exactly the user's "két feltöltött kép amin ugyanaz a nő van … nem jelez" report. The fix makes
 * [DuplicateDetector] fingerprint the pending image backlog on demand before comparing.
 *
 * Runs under real native decoding (no emulator); the legacy shadow fabricates zeroed bitmaps that
 * would make every image hash-collide and prove nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class DuplicateDetectorImageTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var detector: DuplicateDetector
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        detector = DuplicateDetector(
            ctx, repo, PerceptualHashSource(), StructuralFingerprintSource(),
            FakeMediaFingerprintSource(), dispatcher,
        )
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-detector-img").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun imageItem(file: File) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = false,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        type = FileType.IMAGE,
        extension = file.extension,
    )

    /** Draws a recognizable diagonal-gradient "photo" (matches PerceptualHashSourceTest's scene). */
    private fun drawScene(width: Int, height: Int, inverted: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = if (inverted) {
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        } else {
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        }
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors[0], colors[1], Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun save(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat, quality: Int) {
        file.outputStream().use { bitmap.compress(format, quality, it) }
    }

    @Test
    fun recompressedCopyIsFlaggedSimilarAgainstAMetadataOnlyOriginal() = runTest(dispatcher) {
        // The on-device original: a PNG the survey indexed WITHOUT a perceptual hash yet.
        val original = File(tempDir, "original.png")
        save(drawScene(240, 180, inverted = false), original, Bitmap.CompressFormat.PNG, 100)
        repo.indexFile(imageItem(original))
        // Precondition: the original genuinely has NO fingerprint (reproduces the bug's state).
        assertTrue(
            "original must start un-fingerprinted",
            repo.imagesNeedingPerceptualHash(10).any { it.path == original.absolutePath },
        )

        // The freshly-downloaded copy: same picture, different format + resolution + bytes.
        val copy = File(tempDir, "download_FB_IMG.jpg")
        save(drawScene(120, 90, inverted = false), copy, Bitmap.CompressFormat.JPEG, 70)

        val alert = detector.onFileArrived(imageItem(copy))

        assertEquals("a similar-image alert must fire", DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(
            "the alert points at the pre-existing un-backfilled original",
            alert?.existing?.any { it.path == original.absolutePath } == true,
        )
        // The on-demand fingerprint pass persisted, so the backlog is now drained.
        assertTrue(
            "the original is fingerprinted after detection",
            repo.imagesNeedingPerceptualHash(10).none { it.path == original.absolutePath },
        )
    }

    @Test
    fun aDifferentPictureProducesNoSimilarAlert() = runTest(dispatcher) {
        val original = File(tempDir, "scene.png")
        save(drawScene(240, 180, inverted = false), original, Bitmap.CompressFormat.PNG, 100)
        repo.indexFile(imageItem(original))

        // The inverted scene is a structurally different picture — must NOT match.
        val other = File(tempDir, "other.jpg")
        save(drawScene(120, 90, inverted = true), other, Bitmap.CompressFormat.JPEG, 70)

        assertNull(detector.onFileArrived(imageItem(other)))
    }
}
