package com.jupiter.filemanager.feature.search

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultSectionsTest {

    @Test
    fun `direct filename matches are recent and metadata matches are suggestions`() {
        val direct = item("/Documents/Invoices/invoice_2026.pdf", "invoice_2026.pdf", 20L)
        val parentMatch = item("/Documents/invoice 2026/summary.pdf", "summary.pdf", 30L)

        val sections = sectionSearchResults("invoice 2026", listOf(parentMatch, direct))

        assertEquals(listOf(direct.path), sections.recentResults.map(FileItem::path))
        assertEquals(listOf(parentMatch.path), sections.suggestedMatches.map(FileItem::path))
    }

    @Test
    fun `natural language results without literal name match are never discarded`() {
        val items = (1L..6L).map { index ->
            item("/Camera/photo_$index.jpg", "photo_$index.jpg", index)
        }

        val sections = sectionSearchResults("photos from last week", items)

        assertEquals(4, sections.recentResults.size)
        assertEquals(2, sections.suggestedMatches.size)
        assertEquals(items.map(FileItem::path).toSet(),
            (sections.recentResults + sections.suggestedMatches).map(FileItem::path).toSet())
    }

    @Test
    fun `direct matches beyond recent preview remain truthful suggestions`() {
        val items = (1L..6L).map { index ->
            item("/Invoices/invoice_$index.pdf", "invoice_$index.pdf", index)
        }

        val sections = sectionSearchResults("invoice", items)

        assertEquals(6, sections.recentResults.size)
        assertEquals(2, sections.suggestedMatches.size)
        assertEquals(setOf(items[0].path, items[1].path), sections.suggestedMatches.map(FileItem::path).toSet())
    }

    @Test
    fun `parent and mime metadata increase truthful suggestion score`() {
        val parent = item("/Documents/Invoices/report.bin", "report.bin", 1L)
        val unrelated = item("/Music/song.mp3", "song.mp3", 1L)

        assertTrue(searchMetadataScore(parent, "invoices") > searchMetadataScore(unrelated, "invoices"))
    }

    private fun item(path: String, name: String, modified: Long) = FileItem(
        path = path,
        name = name,
        isDirectory = false,
        sizeBytes = 1_024L,
        lastModified = modified,
        type = FileType.DOCUMENT,
        extension = name.substringAfterLast('.', ""),
        mimeType = "application/pdf",
    )
}
