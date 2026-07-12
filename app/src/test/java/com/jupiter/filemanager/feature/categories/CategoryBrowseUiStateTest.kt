package com.jupiter.filemanager.feature.categories

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.model.StorageCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryBrowseUiStateTest {

    @Test
    fun `photo location filters normalize Android and Windows separators`() {
        val camera = item("/storage/emulated/0/DCIM/Camera/photo.jpg")
        val screenshot = item("C:\\Pictures\\Screenshots\\shot.png")
        val download = item("/storage/emulated/0/Download/image.webp")

        assertTrue(PhotoLocationFilter.CAMERA.matches(camera))
        assertTrue(PhotoLocationFilter.SCREENSHOTS.matches(screenshot))
        assertTrue(PhotoLocationFilter.DOWNLOADS.matches(download))
        assertFalse(PhotoLocationFilter.CAMERA.matches(download))
    }

    @Test
    fun `overview fraction and selection use only real visible items`() {
        val first = item("/DCIM/Camera/first.jpg", size = 300L)
        val second = item("/DCIM/Camera/second.jpg", size = 200L)
        val state = CategoryBrowseUiState(
            category = StorageCategory.IMAGES,
            items = listOf(first, second),
            sourceTotalSizeBytes = 1_000L,
            selectedPaths = setOf(second.path, "/stale.jpg"),
        )

        assertEquals(0.5f, state.filteredSizeFraction)
        assertEquals(listOf(second), state.selectedItems)
        assertEquals(1, state.selectedCount)
    }

    @Test
    fun `move busy is derived only from running repository progress`() {
        val running = FileOperationProgress(
            type = FileOperationType.MOVE,
            state = OperationState.RUNNING,
        )
        val completed = running.copy(state = OperationState.COMPLETED)

        assertFalse(CategoryBrowseUiState().isMoveBusy)
        assertTrue(CategoryBrowseUiState(moveOperation = running).isMoveBusy)
        assertFalse(CategoryBrowseUiState(moveOperation = completed).isMoveBusy)
    }

    @Test
    fun `folder picker enables up only below repository root`() {
        val root = CategoryFolderPickerState(
            rootPath = "/storage/emulated/0",
            currentPath = "/storage/emulated/0",
        )
        val child = root.copy(currentPath = "/storage/emulated/0/Pictures")

        assertFalse(root.canNavigateUp)
        assertTrue(child.canNavigateUp)
    }

    private fun item(path: String, size: Long = 1L) = FileItem(
        path = path,
        name = path.replace('\\', '/').substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = size,
        lastModified = 1L,
        type = FileType.IMAGE,
        extension = path.substringAfterLast('.', ""),
        mimeType = "image/jpeg",
    )
}
