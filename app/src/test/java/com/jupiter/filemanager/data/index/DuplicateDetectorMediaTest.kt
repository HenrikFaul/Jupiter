package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
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
import java.io.File

/**
 * Robolectric + in-memory Room proof of the VIDEO / PDF / AUDIO near-duplicate WIRING end-to-end,
 * using a scripted [FakeMediaFingerprintSource] in place of the on-device decoders (which need real
 * codecs the CI lacks). Proves: an arriving media file whose fingerprint is within the per-type
 * Hamming threshold of a metadata-only (un-fingerprinted) original triggers a SIMILAR alert pointing
 * at that original — the on-demand structural backfill fingerprints the original first — and an
 * out-of-threshold file does not. The decode paths themselves are verified on a device.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuplicateDetectorMediaTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var tempDir: File
    private lateinit var ctx: Context

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-detector-media").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    /** A real (tiny) on-disk file so existence-pruning keeps it; content is irrelevant to the fake. */
    private fun realFile(name: String): File =
        File(tempDir, name).apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

    private fun item(file: File, type: FileType) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = false,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        type = type,
        extension = file.extension,
    )

    private fun detectorWith(fake: FakeMediaFingerprintSource) = DuplicateDetector(
        ctx, repo, PerceptualHashSource(), StructuralFingerprintSource(), fake, dispatcher,
    )

    private val base = 0x1234_5678_9ABC_DEF0L
    private val near = base xor 0b11L                 // Hamming distance 2 (≤ every media threshold)
    private val far = base xor -1L                    // Hamming distance 64

    @Test
    fun nearVideoIsFlaggedSimilar() = runTest(dispatcher) {
        val original = realFile("movie.mp4")
        repo.indexFile(item(original, FileType.VIDEO)) // metadata only — no structural fingerprint
        assertTrue(repo.filesNeedingStructuralHash(10).any { it.path == original.absolutePath })
        val copy = realFile("movie-reencoded.mkv")

        val detector = detectorWith(
            FakeMediaFingerprintSource(
                video = mapOf(original.absolutePath to base, copy.absolutePath to near),
            ),
        )
        val alert = detector.onFileArrived(item(copy, FileType.VIDEO))

        assertEquals(DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(alert?.existing?.any { it.path == original.absolutePath } == true)
    }

    @Test
    fun nearPdfIsFlaggedSimilar() = runTest(dispatcher) {
        val original = realFile("report.pdf")
        repo.indexFile(item(original, FileType.PDF))
        val copy = realFile("report-scanned.pdf")

        val detector = detectorWith(
            FakeMediaFingerprintSource(
                pdf = mapOf(original.absolutePath to base, copy.absolutePath to near),
            ),
        )
        val alert = detector.onFileArrived(item(copy, FileType.PDF))

        assertEquals(DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(alert?.existing?.any { it.path == original.absolutePath } == true)
    }

    @Test
    fun nearAudioIsFlaggedSimilar() = runTest(dispatcher) {
        val original = realFile("song.flac")
        repo.indexFile(item(original, FileType.AUDIO))
        val copy = realFile("song.mp3")

        val detector = detectorWith(
            FakeMediaFingerprintSource(
                audio = mapOf(original.absolutePath to base, copy.absolutePath to near),
            ),
        )
        val alert = detector.onFileArrived(item(copy, FileType.AUDIO))

        assertEquals(DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(alert?.existing?.any { it.path == original.absolutePath } == true)
    }

    @Test
    fun farMediaProducesNoAlert() = runTest(dispatcher) {
        val original = realFile("a.mp4")
        repo.indexFile(item(original, FileType.VIDEO))
        val other = realFile("b.mp4")

        val detector = detectorWith(
            FakeMediaFingerprintSource(
                video = mapOf(original.absolutePath to base, other.absolutePath to far),
            ),
        )
        assertNull(detector.onFileArrived(item(other, FileType.VIDEO)))
    }

    @Test
    fun undecodableMediaIsMarkedAndNeverMatches() = runTest(dispatcher) {
        // Two videos the decoder can't read (absent from the fake's map → UNHASHABLE) must never
        // match each other despite sharing the sentinel.
        val a = realFile("broken1.mp4")
        repo.indexFile(item(a, FileType.VIDEO))
        val b = realFile("broken2.mp4")

        val detector = detectorWith(FakeMediaFingerprintSource(/* nothing mapped → UNHASHABLE */))
        assertNull(detector.onFileArrived(item(b, FileType.VIDEO)))
    }
}
