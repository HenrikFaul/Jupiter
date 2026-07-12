package com.jupiter.filemanager.feature.trash

import com.jupiter.filemanager.domain.model.TrashItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TrashUiStateTest {

    private val olderFile = item("old", size = 40L, directory = false, deletedAt = 10L)
    private val newerFile = item("new", size = 10L, directory = false, deletedAt = 30L)
    private val folder = item("folder", size = 100L, directory = true, deletedAt = 20L)

    @Test
    fun `sort changes presentation without dropping entries`() {
        val items = listOf(olderFile, newerFile, folder)

        val newest = TrashUiState(items = items, sort = TrashSort.NEWEST).visibleItems
        val largest = TrashUiState(items = items, sort = TrashSort.SIZE).visibleItems

        assertEquals(listOf("new", "folder", "old"), newest.map(TrashItem::id))
        assertEquals(listOf("folder", "old", "new"), largest.map(TrashItem::id))
        assertEquals(items.map(TrashItem::id).toSet(), largest.map(TrashItem::id).toSet())
    }

    @Test
    fun `type filter exposes real files and folders independently`() {
        val state = TrashUiState(items = listOf(olderFile, newerFile, folder))

        assertEquals(listOf("new", "old"), state.copy(filter = TrashFilter.FILES).visibleItems.map(TrashItem::id))
        assertEquals(listOf("folder"), state.copy(filter = TrashFilter.FOLDERS).visibleItems.map(TrashItem::id))
    }

    private fun item(id: String, size: Long, directory: Boolean, deletedAt: Long) = TrashItem(
        id = id,
        originalPath = "/source/$id",
        name = id,
        sizeBytes = size,
        isDirectory = directory,
        deletedAt = deletedAt,
    )
}
