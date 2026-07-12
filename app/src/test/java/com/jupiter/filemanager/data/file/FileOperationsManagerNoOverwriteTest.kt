package com.jupiter.filemanager.data.file

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.data.index.FileIndexDatabase
import com.jupiter.filemanager.data.index.FileIndexRepositoryImpl
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.TrashItem
import com.jupiter.filemanager.domain.repository.TrashRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression coverage for the transfer no-overwrite contract.
 *
 * A copy/move used to plan `destination/source.name` and open it with
 * `File.outputStream()`, which truncated an unrelated destination file. These tests use real files
 * to prove that an existing target rejects the whole operation before any selected item is written
 * and that both the existing target and every source remain intact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class FileOperationsManagerNoOverwriteTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var database: FileIndexDatabase
    private lateinit var dataSource: FileSystemDataSource
    private lateinit var manager: FileOperationsManager
    private lateinit var root: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FileIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataSource = FileSystemDataSource(context)
        manager = FileOperationsManager(
            dispatcher = dispatcher,
            trashRepository = NoOpTrash,
            fileSystemDataSource = dataSource,
            indexRepository = FileIndexRepositoryImpl(database.fileIndexDao(), dispatcher),
        )
        root = java.nio.file.Files.createTempDirectory("jupiter-no-overwrite").toFile()
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun copy_collisionRejectsEntirePlanAndPreservesExistingDestinationBytes() = runTest(dispatcher) {
        val sourceDir = File(root, "source").apply { mkdirs() }
        val destinationDir = File(root, "destination").apply { mkdirs() }
        val safeSource = File(sourceDir, "safe.txt").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val collidingSource = File(sourceDir, "report.txt").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val protectedDestination = File(destinationDir, "report.txt")
            .apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }

        val emissions = manager.copy(
            items = listOf(dataSource.toFileItem(safeSource), dataSource.toFileItem(collidingSource)),
            destinationPath = destinationDir.absolutePath,
        ).toList()

        assertEquals(OperationState.FAILED, emissions.last().state)
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), protectedDestination.readBytes())
        assertArrayEquals(byteArrayOf(1, 2, 3), safeSource.readBytes())
        assertArrayEquals(byteArrayOf(4, 5, 6), collidingSource.readBytes())
        assertFalse("preflight must write no earlier non-conflicting item", File(destinationDir, "safe.txt").exists())
    }

    @Test
    fun move_collisionPreservesBothSourceAndExistingDestinationBytes() = runTest(dispatcher) {
        val sourceDir = File(root, "source").apply { mkdirs() }
        val destinationDir = File(root, "destination").apply { mkdirs() }
        val source = File(sourceDir, "report.txt").apply { writeBytes(byteArrayOf(1, 3, 3, 7)) }
        val protectedDestination = File(destinationDir, "report.txt")
            .apply { writeBytes(byteArrayOf(7, 3, 3, 1)) }

        val emissions = manager.move(
            items = listOf(dataSource.toFileItem(source)),
            destinationPath = destinationDir.absolutePath,
        ).toList()

        assertEquals(OperationState.FAILED, emissions.last().state)
        assertTrue("a rejected move must not delete its source", source.isFile)
        assertArrayEquals(byteArrayOf(1, 3, 3, 7), source.readBytes())
        assertArrayEquals(byteArrayOf(7, 3, 3, 1), protectedDestination.readBytes())
    }

    private object NoOpTrash : TrashRepository {
        override suspend fun moveToTrash(item: FileItem): Boolean = false
        override fun observeTrash(): Flow<List<TrashItem>> = flowOf(emptyList())
        override fun count(): Flow<Int> = flowOf(0)
        override suspend fun restore(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deletePermanently(id: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun emptyAll(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun purgeOlderThan(cutoffMillis: Long): Int = 0
    }
}
