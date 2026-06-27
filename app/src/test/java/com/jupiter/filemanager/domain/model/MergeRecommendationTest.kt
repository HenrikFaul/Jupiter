package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MergeRecommendationTest {

    private fun file(path: String, size: Long): FileItem =
        FileItem(
            path = path,
            name = path.substringAfterLast('/'),
            isDirectory = false,
            sizeBytes = size,
            lastModified = 0L,
            type = FileType.OTHER,
            extension = "",
        )

    private fun recommendation(files: List<FileItem>): MergeRecommendation {
        val group = DuplicateGroup(hash = "h", files = files)
        return MergeRecommendation(
            group = group,
            recommendedKeepPath = files.first().path,
            removablePaths = files.drop(1).map { it.path },
        )
    }

    @Test
    fun reclaimableBytes_equalsGroupWastedBytes_forMultipleDuplicates() {
        val files = listOf(
            file("/a/x", 100L),
            file("/b/x", 100L),
            file("/c/x", 100L),
        )
        val rec = recommendation(files)
        assertEquals(rec.group.wastedBytes, rec.reclaimableBytes)
        // sanity: two removable copies of 100 bytes each
        assertEquals(200L, rec.reclaimableBytes)
    }

    @Test
    fun reclaimableBytes_equalsGroupWastedBytes_forVaryingSizes() {
        val files = listOf(
            file("/a/x", 10L),
            file("/b/x", 25L),
            file("/c/x", 5L),
            file("/d/x", 60L),
        )
        val rec = recommendation(files)
        assertEquals(rec.group.wastedBytes, rec.reclaimableBytes)
    }

    @Test
    fun reclaimableBytes_isZero_forSingleFileGroup() {
        val files = listOf(file("/a/x", 999L))
        val rec = recommendation(files)
        assertEquals(0L, rec.group.wastedBytes)
        assertEquals(rec.group.wastedBytes, rec.reclaimableBytes)
    }

    @Test
    fun reclaimableBytes_isZero_forEmptyGroup() {
        val group = DuplicateGroup(hash = "h", files = emptyList())
        val rec = MergeRecommendation(
            group = group,
            recommendedKeepPath = "",
            removablePaths = emptyList(),
        )
        assertEquals(0L, rec.group.wastedBytes)
        assertEquals(rec.group.wastedBytes, rec.reclaimableBytes)
    }

    @Test
    fun reclaimableBytes_alwaysTracksGroupWastedBytes() {
        val files = listOf(
            file("/a/x", 1L),
            file("/b/x", 2L),
        )
        val rec = recommendation(files)
        assertEquals(rec.reclaimableBytes, rec.group.wastedBytes)
    }
}
