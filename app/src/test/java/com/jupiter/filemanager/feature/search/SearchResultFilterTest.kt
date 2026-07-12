package com.jupiter.filemanager.feature.search

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.FilterOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultFilterTest {

    @Test
    fun `files and folders scopes use directory state rather than a fabricated type`() {
        val folder = item(name = "Projects", directory = true, type = FileType.FOLDER)
        val file = item(name = "project.txt", directory = false, type = FileType.DOCUMENT)

        assertTrue(SearchResultFilter.FOLDERS.matches(folder))
        assertFalse(SearchResultFilter.FOLDERS.matches(file))
        assertTrue(SearchResultFilter.FILES.matches(file))
        assertFalse(SearchResultFilter.FILES.matches(folder))
    }

    @Test
    fun `pdf and image scopes narrow both repository filter and visible results`() {
        val pdf = item(name = "invoice.pdf", directory = false, type = FileType.PDF)
        val image = item(name = "invoice.png", directory = false, type = FileType.IMAGE)
        val base = FilterOption(query = "invoice")

        assertEquals(FileType.PDF, SearchResultFilter.PDFS.applyTo(base).typeFilter)
        assertTrue(SearchResultFilter.PDFS.matches(pdf))
        assertFalse(SearchResultFilter.PDFS.matches(image))

        assertEquals(FileType.IMAGE, SearchResultFilter.IMAGES.applyTo(base).typeFilter)
        assertTrue(SearchResultFilter.IMAGES.matches(image))
        assertFalse(SearchResultFilter.IMAGES.matches(pdf))
    }

    @Test
    fun `ai scope preserves existing natural language filter rather than overriding it`() {
        val parsed = FilterOption(query = "last week", typeFilter = FileType.PDF)

        assertEquals(parsed, SearchResultFilter.AI_SEARCH.applyTo(parsed))
        assertTrue(SearchResultFilter.AI_SEARCH.enablesNaturalLanguage)
        assertFalse(SearchResultFilter.ALL.enablesNaturalLanguage)
    }

    private fun item(name: String, directory: Boolean, type: FileType): FileItem = FileItem(
        path = "/storage/emulated/0/$name",
        name = name,
        isDirectory = directory,
        sizeBytes = 0,
        lastModified = 0,
        type = type,
        extension = "",
    )
}
