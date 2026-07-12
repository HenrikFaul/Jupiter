package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.MediaQuality
import com.jupiter.filemanager.domain.model.QualityKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateSelectionPolicyTest {

    @Test
    fun visibleSelectionProtectsQualityRankedKeeperAndIgnoresHiddenTab() {
        val best = file("/exact/best.jpg", size = 50, modified = 10)
        val extra = file("/exact/extra.jpg", size = 100, modified = 20)
        val hiddenA = file("/similar/a.jpg", size = 200, modified = 30)
        val hiddenB = file("/similar/b.jpg", size = 150, modified = 40)
        val exact = DuplicateGroup("exact", listOf(best, extra))
        val similar = DuplicateGroup("similar", listOf(hiddenA, hiddenB), similar = true)
        val qualities = mapOf(
            best.path to quality(1_000),
            extra.path to quality(10),
        )
        val state = DuplicatesUiState(
            groups = listOf(exact, similar),
            qualities = qualities,
            presentation = DuplicatePresentation.EXACT,
        )

        val selected = DuplicateSelectionPolicy.removablePaths(
            actionableGroups = state.visibleGroups,
            allGroups = state.groups,
            qualities = state.qualities,
        )

        assertEquals(setOf(extra.path), selected)
        assertFalse(best.path in selected)
        assertFalse(hiddenA.path in selected)
        assertFalse(hiddenB.path in selected)
    }

    @Test
    fun overlappingGroupsStillRetainAtLeastOneCopyPerGroup() {
        val a = file("/a", size = 300, modified = 3)
        val b = file("/b", size = 200, modified = 2)
        val c = file("/c", size = 100, modified = 1)
        val first = DuplicateGroup("first", listOf(a, b))
        val second = DuplicateGroup("second", listOf(b, c))
        val groups = listOf(first, second)

        val selected = DuplicateSelectionPolicy.removablePaths(
            actionableGroups = groups,
            allGroups = groups,
            qualities = emptyMap(),
        )

        assertTrue(first.files.any { it.path !in selected })
        assertTrue(second.files.any { it.path !in selected })
        assertFalse(a.path in selected)
        assertFalse(b.path in selected)
        assertEquals(setOf(c.path), selected)
    }

    @Test
    fun switchingTabOrSizeScopePrunesHiddenDeletionSelection() {
        val exactBest = file("/exact/best", size = 5_000, modified = 2)
        val exactExtra = file("/exact/extra", size = 4_000, modified = 1)
        val similarBest = file("/similar/best", size = 3_000, modified = 2)
        val similarExtra = file("/similar/extra", size = 2_000, modified = 1)
        val exact = DuplicateGroup("exact", listOf(exactBest, exactExtra))
        val similar = DuplicateGroup("similar", listOf(similarBest, similarExtra), similar = true)

        val similarState = DuplicatesUiState(
            groups = listOf(exact, similar),
            selectedPaths = setOf(exactExtra.path, similarExtra.path),
            presentation = DuplicatePresentation.SIMILAR,
        )
        assertEquals(
            setOf(similarExtra.path),
            DuplicateSelectionPolicy.sanitizeVisible(similarState),
        )

        val filteredOut = similarState.copy(sizeFilter = SizeFilter.MB_1)
        assertTrue(DuplicateSelectionPolicy.sanitizeVisible(filteredOut).isEmpty())
    }

    private fun file(path: String, size: Long, modified: Long) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = size,
        lastModified = modified,
        type = FileType.IMAGE,
        extension = "jpg",
    )

    private fun quality(score: Long) = MediaQuality(
        kind = QualityKind.IMAGE,
        score = score,
    )
}
