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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real-file proof of the cascading exact-duplicate pipeline
 * (SIZE → QUICK head+tail HASH → STRONG full hash):
 *
 *  - byte-identical files still group (the cascade changes cost, never the result);
 *  - same-size files that differ only in the MIDDLE — where the quick head+tail hash
 *    COLLIDES — are still separated by the strong full-content hash (the quick hash is a
 *    pre-filter, never the grouping authority);
 *  - the computed quick hash is persisted so the next scan skips the read.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class QuickHashCascadeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-quickhash").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private suspend fun indexed(name: String, bytes: ByteArray): File {
        val file = File(tempDir, name).apply { writeBytes(bytes) }
        val item = FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = false,
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
            type = FileType.OTHER,
            extension = "bin",
        )
        repo.upsert(listOf(item))
        return file
    }

    @Test
    fun identicalFilesGroupAndDifferentHeadsAreSplitByTheQuickStage() = runTest(dispatcher) {
        val payload = ByteArray(8 * 1024) { (it % 251).toByte() }
        val a = indexed("a.bin", payload)
        val b = indexed("b.bin", payload) // byte-identical copy
        // Same SIZE but a different head → the quick stage already separates it.
        val other = payload.copyOf().also { it[0] = 99 }
        indexed("c.bin", other)

        val groups = repo.duplicateGroups(minSizeBytes = 1024L)

        assertEquals("exactly one duplicate group expected", 1, groups.size)
        assertEquals(
            setOf(a.absolutePath, b.absolutePath),
            groups.first().files.map { it.path }.toSet(),
        )
        // The quick hash was persisted for reuse on the next scan.
        val row = db.fileIndexDao().getByPath(a.absolutePath)!!
        assertNotNull(row.quickDigest)
        assertEquals("legacy TEXT must not consume duplicate storage", null, row.quickHash)
    }

    @Test
    fun middleOnlyDifferenceCollidesOnQuickHashButIsSplitByTheStrongHash() = runTest(dispatcher) {
        // 300 KiB files: identical first/last 64 KiB (the quick window), different MIDDLE.
        val size = 300 * 1024
        val base = ByteArray(size) { (it % 199).toByte() }
        val middleDiff = base.copyOf().also { it[size / 2] = 77 }

        val a = indexed("same1.bin", base)
        val b = indexed("same2.bin", base)
        indexed("middle.bin", middleDiff)

        val groups = repo.duplicateGroups(minSizeBytes = 1024L)

        assertEquals("the middle-diff file must NOT join the group", 1, groups.size)
        assertEquals(
            setOf(a.absolutePath, b.absolutePath),
            groups.first().files.map { it.path }.toSet(),
        )
        // Sanity: the quick hashes really did collide (head+tail identical), proving the
        // strong hash — not the quick pre-filter — made the final call.
        val quickA = db.fileIndexDao().getByPath(a.absolutePath)!!.quickDigest
        val quickMiddle = db.fileIndexDao()
            .getByPath(File(tempDir, "middle.bin").absolutePath)!!.quickDigest
        assertNotNull(quickA)
        assertNotNull(quickMiddle)
        assertTrue(
            "quick hashes must collide for a middle-only diff",
            quickA!!.contentEquals(quickMiddle!!),
        )
    }
}
