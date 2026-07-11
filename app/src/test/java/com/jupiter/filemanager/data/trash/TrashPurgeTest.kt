package com.jupiter.filemanager.data.trash

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.data.file.FileSystemDataSource
import com.jupiter.filemanager.data.index.FileIndexDatabase
import com.jupiter.filemanager.data.index.FileIndexRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Real proof of the Recycle-Bin retention sweep ([TrashRepositoryImpl.purgeOlderThan]): items
 * trashed before the cutoff are permanently removed (row AND on-disk payload), newer ones are kept,
 * and a row whose payload is already gone is still cleaned up. Backs the new "auto-delete after N
 * days" setting — the daily worker computes the cutoff and calls exactly this.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TrashPurgeTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var trashDb: TrashDatabase
    private lateinit var indexDb: FileIndexDatabase
    private lateinit var repo: TrashRepositoryImpl
    private lateinit var dao: TrashDao
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        trashDb = Room.inMemoryDatabaseBuilder(ctx, TrashDatabase::class.java)
            .allowMainThreadQueries().build()
        indexDb = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        dao = trashDb.trashDao()
        val index = FileIndexRepositoryImpl(indexDb.fileIndexDao(), dispatcher)
        repo = TrashRepositoryImpl(ctx, dao, dispatcher, index, FileSystemDataSource(ctx))
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-trash-purge").toFile()
    }

    @After
    fun tearDown() {
        trashDb.close()
        indexDb.close()
        tempDir.deleteRecursively()
    }

    private suspend fun insert(id: String, deletedAt: Long): File {
        val payload = File(tempDir, "$id.bin").apply { writeText("x") }
        dao.insert(
            TrashEntry(
                id = id,
                originalPath = "/orig/$id",
                trashedPath = payload.absolutePath,
                name = "$id.bin",
                sizeBytes = 1L,
                isDirectory = false,
                deletedAt = deletedAt,
            ),
        )
        return payload
    }

    @Test
    fun purgeRemovesItemsOlderThanCutoffAndKeepsNewer() = runTest(dispatcher) {
        val cutoff = 10_000L
        val old = insert("old", deletedAt = 5_000L)      // before cutoff → purged
        val boundary = insert("edge", deletedAt = 10_000L) // == cutoff → kept (strictly-before)
        val fresh = insert("fresh", deletedAt = 20_000L)  // after cutoff → kept

        val purged = repo.purgeOlderThan(cutoff)

        assertEquals(1, purged)
        // Old item: row gone, payload deleted.
        assertNull(dao.getById("old"))
        assertFalse("old payload must be deleted", old.exists())
        // Boundary + fresh: rows and payloads intact.
        assertTrue(boundary.exists())
        assertTrue(fresh.exists())
        assertEquals(2, dao.getAll().size)
    }

    @Test
    fun purgeCleansUpRowWhosePayloadIsAlreadyGone() = runTest(dispatcher) {
        val ghost = insert("ghost", deletedAt = 1_000L)
        ghost.delete() // payload vanished out from under us

        val purged = repo.purgeOlderThan(cutoffMillis = 5_000L)

        assertEquals(1, purged)
        assertNull(dao.getById("ghost"))
    }

    @Test
    fun nothingIsPurgedWhenAllItemsAreNewerThanCutoff() = runTest(dispatcher) {
        insert("a", deletedAt = 100_000L)
        insert("b", deletedAt = 200_000L)

        val purged = repo.purgeOlderThan(cutoffMillis = 50_000L)

        assertEquals(0, purged)
        assertEquals(2, dao.getAll().size)
    }
}
