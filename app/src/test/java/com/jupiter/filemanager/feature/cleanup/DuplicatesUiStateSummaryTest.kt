package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesUiStateSummaryTest {

    @Test
    fun heroAndTabsCountExactAndSimilarItemsIndependentOfSelectedTab() {
        val exact = DuplicateGroup("exact", listOf(file("/a"), file("/b")))
        val similar = DuplicateGroup("similar", listOf(file("/c"), file("/d"), file("/e")), similar = true)
        val state = DuplicatesUiState(
            groups = listOf(exact, similar),
            presentation = DuplicatePresentation.EXACT,
        )

        assertEquals(5, state.totalDuplicateItemCount)
        assertEquals(2, state.duplicateItemCount(DuplicatePresentation.EXACT))
        assertEquals(3, state.duplicateItemCount(DuplicatePresentation.SIMILAR))
    }

    @Test
    fun summaryDoesNotDoubleCountAPathFromOverlappingDetectorGroups() {
        val exact = DuplicateGroup("exact", listOf(file("/a"), file("/shared")))
        val similar = DuplicateGroup("similar", listOf(file("/shared"), file("/b")), similar = true)

        assertEquals(3, DuplicatesUiState(groups = listOf(exact, similar)).totalDuplicateItemCount)
    }

    private fun file(path: String) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = 2L * 1024 * 1024,
        lastModified = 1L,
        type = FileType.IMAGE,
        extension = "jpg",
    )
}
