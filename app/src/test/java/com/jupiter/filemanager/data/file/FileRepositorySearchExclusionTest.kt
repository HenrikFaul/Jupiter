package com.jupiter.filemanager.data.file

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.index.FileIndexDatabase
import com.jupiter.filemanager.data.index.FileIndexRepositoryImpl
import com.jupiter.filemanager.data.storage.StorageVolumeProvider
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.TrashItem
import com.jupiter.filemanager.domain.repository.TrashRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real proof that the filesystem-walk SEARCH surface — which backs the Recent tab and global
 * search's walk fallback — no longer surfaces a Samsung recycle-bin (`Android/.Trash/…`) file.
 *
 * This was the last enumerator that bypassed [com.jupiter.filemanager.core.util.StorageExclusions]:
 * the trashed `.apk` is NOT dot-prefixed, so `showHidden = false` did not hide it, and it landed
 * at the top of the Recent tab (recent mtime) where tapping it opened Preview to "Not found".
 *
 * The test builds a REAL directory tree on disk (mirroring the reported Samsung path) and runs the
 * real [FileRepositoryImpl.search]; the collaborators search() does not use are minimally stubbed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FileRepositorySearchExclusionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileRepositoryImpl
    private lateinit var root: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        val index = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        val dataSource = FileSystemDataSource(ctx)
        val ops = FileOperationsManager(dispatcher, NoOpTrash, dataSource, index)
        val volumes = StorageVolumeProvider(ctx)
        repo = FileRepositoryImpl(dataSource, ops, volumes, index, dispatcher)

        root = java.nio.file.Files.createTempDirectory("jupiter-search-excl").toFile()
        // A normal, openable file that search MUST return.
        write(File(root, "Download/report.apk"))
        // Samsung My Files recycle-bin file — same structure as the reported "Not found" path.
        // Deliberately NOT dot-prefixed, so only the segment exclusion (not showHidden) can drop it.
        write(
            File(
                root,
                "Android/.Trash/com.sec.android.app.myfiles/7151d159/1782424126989/" +
                    "storage/emulated/0/Download/mediadownload (9).apk",
            ),
        )
        // A thumbnail-cache file (another excluded segment) that must also stay hidden.
        write(File(root, "DCIM/.thumbnails/1234.jpg"))
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun searchExcludesTrashAndThumbnailFilesButKeepsNormalOnes() = runTest(dispatcher) {
        // Empty query → name filter matches everything; only the segment exclusion can remove a row.
        val results = repo.search(root.absolutePath, FilterOption()).toList()
        val names = results.map { it.name }

        assertTrue("normal file must be searchable", names.contains("report.apk"))
        assertFalse(
            "Samsung .Trash file must never be surfaced by search",
            names.contains("mediadownload (9).apk"),
        )
        assertFalse(
            "thumbnail-cache file must never be surfaced by search",
            names.contains("1234.jpg"),
        )
        // And nothing returned may live under an excluded segment.
        assertTrue(
            "no result may lie in a trash/thumbnail dir",
            results.none { it.path.lowercase().let { p -> "/.trash/" in p || "/.thumbnails/" in p } },
        )
    }

    private fun write(file: File) {
        file.parentFile?.mkdirs()
        file.writeText("x")
    }

    /** search() never touches the trash repo; a no-op keeps [FileOperationsManager] constructible. */
    private object NoOpTrash : TrashRepository {
        override suspend fun moveToTrash(item: FileItem): Boolean = false
        override fun observeTrash(): Flow<List<TrashItem>> = flowOf(emptyList())
        override fun count(): Flow<Int> = flowOf(0)
        override suspend fun restore(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deletePermanently(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun emptyAll(): AppResult<Unit> = AppResult.Success(Unit)
    }
}
