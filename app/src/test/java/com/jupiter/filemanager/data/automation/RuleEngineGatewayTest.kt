package com.jupiter.filemanager.data.automation

import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.domain.model.AutomationRule
import com.jupiter.filemanager.domain.model.Bookmark
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.SortOption
import com.jupiter.filemanager.domain.model.StorageVolumeInfo
import com.jupiter.filemanager.domain.repository.BookmarkRepository
import com.jupiter.filemanager.domain.repository.FileRepository
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEngineGatewayTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun previewIsReadOnlyAndExecutionRoutesMoveThroughRepositoryGateway() = runTest {
        val root = temporaryFolder.root
        val downloads = File(root, "Download").apply { mkdirs() }
        val pdf = File(downloads, "invoice.pdf").apply { writeText("invoice") }
        val repository = RecordingFileRepository(root)
        val engine = RuleEngine(
            fileRepository = repository,
            bookmarkRepository = NoOpBookmarkRepository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val rule = AutomationRule(
            id = "pdf",
            name = "PDF sorter",
            enabled = true,
            whenText = "PDF files in Downloads",
            thenText = "move it to Documents",
        )

        val preview = engine.preview(rule)
        assertEquals(1, preview.matchingFiles)
        assertEquals(0, repository.moveCalls)
        assertTrue(pdf.exists())

        assertEquals(1, engine.applyAll(listOf(rule)))
        assertEquals(1, repository.moveCalls)
        assertEquals(File(root, "Documents").absolutePath, repository.lastDestination)
        assertTrue(pdf.exists()) // the fake gateway proves RuleEngine itself never deletes it
    }

    @Test
    fun legacyDeleteRuleCannotReachAnyFileMutationGateway() = runTest {
        val root = temporaryFolder.root
        val downloads = File(root, "Download").apply { mkdirs() }
        val pdf = File(downloads, "keep.pdf").apply { writeText("keep") }
        val repository = RecordingFileRepository(root)
        val engine = RuleEngine(
            fileRepository = repository,
            bookmarkRepository = NoOpBookmarkRepository,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        val legacyDelete = AutomationRule(
            id = "legacy",
            name = "Old unsafe rule",
            enabled = true,
            whenText = "PDF files in Downloads",
            thenText = "delete files",
        )

        assertEquals(0, engine.applyAll(listOf(legacyDelete)))
        assertEquals(0, repository.moveCalls)
        assertTrue(pdf.exists())
    }

    private class RecordingFileRepository(private val root: File) : FileRepository {
        var moveCalls: Int = 0
        var lastDestination: String? = null

        override fun rootDirectory(): String = root.absolutePath

        override suspend fun getFile(path: String): AppResult<FileItem> {
            val file = File(path)
            return AppResult.Success(
                FileItem(
                    path = file.absolutePath,
                    name = file.name,
                    isDirectory = false,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    type = FileType.DOCUMENT,
                    extension = file.extension,
                ),
            )
        }

        override fun move(
            items: List<FileItem>,
            destinationPath: String,
        ): Flow<FileOperationProgress> {
            moveCalls += 1
            lastDestination = destinationPath
            return flowOf(
                FileOperationProgress(
                    type = FileOperationType.MOVE,
                    state = OperationState.COMPLETED,
                    processedItems = items.size,
                    totalItems = items.size,
                ),
            )
        }

        override fun observeDirectory(path: String, sort: SortOption, filter: FilterOption) =
            emptyFlow<AppResult<List<FileItem>>>()

        override suspend fun listFiles(path: String, sort: SortOption, filter: FilterOption) =
            error("Not used")

        override suspend fun listFromIndex(path: String, sort: SortOption, filter: FilterOption) =
            emptyList<FileItem>()

        override suspend fun createFolder(parentPath: String, name: String) = error("Not used")
        override suspend fun rename(item: FileItem, newName: String) = error("Not used")
        override suspend fun delete(items: List<FileItem>) = error("Not used")
        override fun copy(items: List<FileItem>, destinationPath: String) =
            emptyFlow<FileOperationProgress>()

        override fun search(rootPath: String, filter: FilterOption) = emptyFlow<FileItem>()
        override fun storageVolumes(): List<StorageVolumeInfo> = emptyList()
    }

    private object NoOpBookmarkRepository : BookmarkRepository {
        override fun observeBookmarks(): Flow<List<Bookmark>> = emptyFlow()
        override suspend fun addBookmark(path: String, label: String) = Unit
        override suspend fun removeBookmark(path: String) = Unit
        override fun observeRecents(): Flow<List<String>> = emptyFlow()
        override suspend fun addRecent(path: String) = Unit
    }
}
