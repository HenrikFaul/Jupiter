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
 * Robolectric + in-memory Room test of [DuplicateDetector] over REAL files, asserting directly on
 * `onFileArrived`'s return value (no SharedFlow-collection timing). This proves the exact scenario
 * the user hit: a byte-identical copy of a file whose original was indexed with METADATA ONLY (no
 * hash — the realistic post-survey state) is detected as an EXACT duplicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuplicateDetectorTest {

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
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-detector").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun item(file: File) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = false,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        type = FileType.OTHER,
        extension = file.extension,
    )

    @Test
    fun exactByteIdenticalCopyIsDetectedAgainstAMetadataOnlyOriginal() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 251).toByte() }
        val original = File(tempDir, "photo.jpg").apply { writeBytes(payload) }
        repo.indexFile(item(original)) // survey-style: metadata only, no hash

        val copy = File(tempDir, "photo (1).jpg").apply { writeBytes(payload) }

        val alert = detector.onFileArrived(item(copy))

        assertEquals(DuplicateKind.EXACT, alert?.kind)
        assertEquals(copy.absolutePath, alert?.newFile?.path)
        assertTrue(
            "the alert points at the pre-existing original",
            alert?.existing?.any { it.path == original.absolutePath } == true,
        )
    }

    @Test
    fun aUniqueFileProducesNoAlert() = runTest(dispatcher) {
        val unique = File(tempDir, "unique.bin").apply { writeBytes(ByteArray(9000) { it.toByte() }) }
        assertNull(detector.onFileArrived(item(unique)))
    }

    @Test
    fun tinyFilesNeverAlert() = runTest(dispatcher) {
        // Below the 4 KiB alert floor: empty placeholders must never spam alerts.
        val a = File(tempDir, "a.nomedia").apply { writeBytes(ByteArray(0)) }
        val b = File(tempDir, "b.nomedia").apply { writeBytes(ByteArray(0)) }
        repo.indexFile(item(a))
        assertNull(detector.onFileArrived(item(b)))
    }
}
