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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Robolectric + in-memory Room proof that the live arrival pipeline now flags NON-image
 * near-duplicates too:
 *  - a reformatted copy of a code file the survey indexed with metadata only (no structural
 *    fingerprint) → SIMILAR alert (text SimHash, on-demand backfilled before comparison);
 *  - an archive repacked with different compression (different bytes) → SIMILAR alert (member-tree).
 * These are the text/code and archive layers the blueprint specified, wired end-to-end and verified
 * in pure-JVM CI (no emulator / media decode required).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuplicateDetectorStructuralTest {

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
            FakeMediaFingerprintSource(), db.dedupDecisionDao(), dispatcher,
        )
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-detector-struct").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun item(file: File, type: FileType) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = false,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        type = type,
        extension = file.extension,
    )

    private fun writeZip(file: File, level: Int, members: List<Pair<String, String>>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.setLevel(level)
            for ((name, content) in members) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun reformattedCodeCopyIsFlaggedSimilar() = runTest(dispatcher) {
        val original = File(tempDir, "Original.kt").apply {
            writeText("fun greet(name: String) {\n    println(\"Hello, \$name\")\n}\n")
        }
        repo.indexFile(item(original, FileType.CODE)) // metadata only — no structural hash yet
        assertTrue(
            "original starts without a structural fingerprint",
            repo.filesNeedingStructuralHash(10).any { it.path == original.absolutePath },
        )

        // Re-indented, whitespace-shuffled copy — different bytes, same tokens.
        val copy = File(tempDir, "Copy.kt").apply {
            writeText("fun greet(name:String){\n\n  println(\"Hello, \$name\")\n\n}\n")
        }

        val alert = detector.onFileArrived(item(copy, FileType.CODE))

        assertEquals(DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(
            "alert points at the pre-existing original",
            alert?.existing?.any { it.path == original.absolutePath } == true,
        )
    }

    @Test
    fun repackedArchiveIsFlaggedSameContents() = runTest(dispatcher) {
        val members = listOf("readme.txt" to "the same payload", "data/x.bin" to "0123456789")
        val original = File(tempDir, "bundle.zip").apply { writeZip(this, 0, members) }
        repo.indexFile(item(original, FileType.ARCHIVE))

        // Identical members, maximum compression → different bytes, same contents.
        val repacked = File(tempDir, "bundle-recompressed.zip").apply { writeZip(this, 9, members) }

        val alert = detector.onFileArrived(item(repacked, FileType.ARCHIVE))

        assertEquals(DuplicateKind.SIMILAR, alert?.kind)
        assertTrue(
            "alert points at the pre-existing archive",
            alert?.existing?.any { it.path == original.absolutePath } == true,
        )
    }

    @Test
    fun unrelatedCodeAndArchiveProduceNoAlert() = runTest(dispatcher) {
        val codeA = File(tempDir, "A.kt").apply { writeText("val a = listOf(1, 2, 3).map { it * 2 }") }
        repo.indexFile(item(codeA, FileType.CODE))
        val codeB = File(tempDir, "B.kt").apply {
            writeText("interface Clock { fun now(): Long }\nobject SystemClock : Clock { override fun now() = 0L }")
        }
        assertNull(detector.onFileArrived(item(codeB, FileType.CODE)))

        val zipA = File(tempDir, "one.zip").apply { writeZip(this, 6, listOf("p.txt" to "alpha")) }
        repo.indexFile(item(zipA, FileType.ARCHIVE))
        val zipB = File(tempDir, "two.zip").apply { writeZip(this, 6, listOf("q.txt" to "completely different member")) }
        assertNull(detector.onFileArrived(item(zipB, FileType.ARCHIVE)))
    }
}
