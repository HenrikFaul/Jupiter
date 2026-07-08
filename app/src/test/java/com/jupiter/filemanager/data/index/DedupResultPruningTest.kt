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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric + in-memory Room proof of the "opening a duplicate errors" bug: a duplicate whose
 * indexed path lives in a trash/recycle-bin staging dir (Samsung `Android/.Trash/...`), or whose
 * file has since vanished, is never surfaced as a content-duplicate — and the dead row is pruned
 * from the index when the dedup surface is consulted. Before the fix these rows were returned, so
 * tapping them opened the preview to "Not found: …/Android/.Trash/…".
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DedupResultPruningTest {

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
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-prune").toFile()
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
    fun aTrashedDuplicateIsNeitherSurfacedNorKept() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 251).toByte() }
        // The copy sits in a Samsung-style trash staging dir (capital `.Trash`, matched
        // case-insensitively). It physically exists but must never be offered as a duplicate.
        val trashDir = File(tempDir, "Android/.Trash/com.sec.android.app.myfiles/123").apply { mkdirs() }
        val trashed = File(trashDir, "mediadownload.apk").apply { writeBytes(payload) }
        repo.indexFile(item(trashed))

        val arriving = File(tempDir, "mediadownload.apk").apply { writeBytes(payload) }

        val dups = repo.findContentDuplicates(item(arriving))

        assertTrue("a trashed copy must not be surfaced as a duplicate", dups.isEmpty())
        assertNull("the trashed row is pruned from the index", db.fileIndexDao().getByPath(trashed.absolutePath))
    }

    @Test
    fun aVanishedDuplicateIsNeitherSurfacedNorKept() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 241).toByte() }
        val original = File(tempDir, "vanisher.bin").apply { writeBytes(payload) }
        val copy = File(tempDir, "vanisher (1).bin").apply { writeBytes(payload) }
        // Index BOTH with a real content hash (survey-precomputed), then delete the copy from disk.
        repo.indexFile(item(original))
        repo.indexFile(item(copy))
        repo.findContentDuplicates(item(original)) // hashes + caches both rows
        assertTrue("copy hashed and cached", copy.delete())

        val dups = repo.findContentDuplicates(item(original))

        assertFalse("a vanished copy must not be surfaced", dups.any { it.path == copy.absolutePath })
        assertNull("the vanished row is pruned", db.fileIndexDao().getByPath(copy.absolutePath))
    }

    @Test
    fun aLiveDuplicateIsStillSurfaced() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 239).toByte() }
        val original = File(tempDir, "keep.bin").apply { writeBytes(payload) }
        val copy = File(tempDir, "keep (1).bin").apply { writeBytes(payload) }
        repo.indexFile(item(original))
        repo.indexFile(item(copy))

        val dups = repo.findContentDuplicates(item(original))

        assertTrue("a live duplicate is still detected", dups.any { it.path == copy.absolutePath })
    }
}
