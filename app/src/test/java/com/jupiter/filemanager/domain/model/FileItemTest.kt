package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [FileItem.parentPath].
 *
 * Implementation under test:
 *   path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }
 */
class FileItemTest {

    private fun item(path: String): FileItem = FileItem(
        path = path,
        name = path.trimEnd('/').substringAfterLast('/').ifEmpty { path },
        isDirectory = true,
        sizeBytes = 0L,
        lastModified = 0L,
        type = FileType.FOLDER,
        extension = "",
    )

    @Test
    fun nestedPath_returnsParent() {
        assertEquals("/storage/emulated/0", item("/storage/emulated/0/Download").parentPath)
    }

    @Test
    fun deeplyNestedPath_returnsImmediateParent() {
        assertEquals("/a/b/c", item("/a/b/c/d").parentPath)
    }

    @Test
    fun rootSlash_returnsNull() {
        assertNull(item("/").parentPath)
    }

    @Test
    fun multipleRootSlashes_returnsNull() {
        // "///" trimEnd('/') -> "" -> substringBeforeLast -> "" -> null
        assertNull(item("///").parentPath)
    }

    @Test
    fun singleSegmentWithLeadingSlash_returnsEmptyTrimmedToNull() {
        // "/Download" trimEnd -> "/Download" -> substringBeforeLast('/') -> "" -> null
        assertNull(item("/Download").parentPath)
    }

    @Test
    fun singleSegmentNoSlash_returnsNull() {
        // "Download" has no '/', substringBeforeLast returns "" (the missingDelimiterValue) -> null
        assertNull(item("Download").parentPath)
    }

    @Test
    fun emptyPath_returnsNull() {
        assertNull(item("").parentPath)
    }

    @Test
    fun trailingSlash_isHandledLikeNoTrailingSlash() {
        // Trailing slash trimmed before computing parent.
        assertEquals("/storage/emulated/0", item("/storage/emulated/0/Download/").parentPath)
    }

    @Test
    fun trailingSlash_singleSegment_returnsNull() {
        // "/Download/" trimEnd -> "/Download" -> substringBeforeLast -> "" -> null
        assertNull(item("/Download/").parentPath)
    }

    @Test
    fun multipleTrailingSlashes_areAllTrimmed() {
        assertEquals("/a/b", item("/a/b/c///").parentPath)
    }

    @Test
    fun nestedRelativePath_returnsParent() {
        // No leading slash; parent is the segment(s) before the last '/'.
        assertEquals("a/b", item("a/b/c").parentPath)
    }

    @Test
    fun twoSegmentRelativePath_returnsFirstSegment() {
        assertEquals("a", item("a/b").parentPath)
    }
}
