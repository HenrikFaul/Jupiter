package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure proof of the duplicate-list size filter: a group is visible when at least one of its copies
 * is at least the selected minimum size, so tiny few-KB image duplicates can be hidden to focus on
 * the space-worth multi-MB / multi-GB ones.
 */
class DuplicatesUiStateSizeFilterTest {

    private fun file(size: Long) = FileItem(
        path = "/s/$size-${System.identityHashCode(Any())}",
        name = "f",
        isDirectory = false,
        sizeBytes = size,
        lastModified = 0L,
        type = FileType.IMAGE,
        extension = "jpg",
    )

    private fun group(hash: String, vararg sizes: Long) =
        DuplicateGroup(hash = hash, files = sizes.map { file(it) })

    private val tiny = group("a", 50L * 1024, 50L * 1024) // 50 KB copies
    private val medium = group("b", 3L * 1024 * 1024, 3L * 1024 * 1024) // 3 MB copies
    private val large = group("c", 200L * 1024 * 1024, 10L * 1024) // one 200 MB, one 10 KB copy

    private fun state(filter: SizeFilter) =
        DuplicatesUiState(groups = listOf(tiny, medium, large), sizeFilter = filter)

    @Test
    fun allSizesShowsEveryGroup() {
        assertEquals(listOf("c", "b", "a"), state(SizeFilter.ALL).visibleGroups.map { it.hash })
    }

    @Test
    fun oneMegabyteHidesTheTinyGroup() {
        assertEquals(listOf("c", "b"), state(SizeFilter.MB_1).visibleGroups.map { it.hash })
    }

    @Test
    fun hundredMegabyteKeepsOnlyTheLargeGroup() {
        // The large group qualifies on its 200 MB copy even though its other copy is 10 KB.
        assertEquals(listOf("c"), state(SizeFilter.MB_100).visibleGroups.map { it.hash })
    }

    @Test
    fun hundredKilobyteHidesOnlyTheFiftyKbGroup() {
        assertEquals(listOf("c", "b"), state(SizeFilter.KB_100).visibleGroups.map { it.hash })
    }

    @Test
    fun smallestFirstRestoresAscendingSizeReviewOrder() {
        val ordered = state(SizeFilter.ALL).copy(sizeOrder = DuplicateSizeOrder.SMALLEST_FIRST)
        assertEquals(listOf("a", "b", "c"), ordered.visibleGroups.map { it.hash })
    }
}
