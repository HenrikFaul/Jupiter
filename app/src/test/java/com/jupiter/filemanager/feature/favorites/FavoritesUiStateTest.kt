package com.jupiter.filemanager.feature.favorites

import com.jupiter.filemanager.domain.model.Bookmark
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesUiStateTest {

    private fun entryFixture(): FavoriteEntry =
        FavoriteEntry(
            bookmark = Bookmark(path = "/storage/emulated/0/Documents", label = "Documents"),
            item = null,
            isDirectory = true,
        )

    @Test
    fun isEmpty_true_whenNotLoadingAndNoEntries() {
        val state = FavoritesUiState(isLoading = false, entries = emptyList())
        assertTrue(state.isEmpty)
    }

    @Test
    fun isEmpty_false_whileLoadingWithNoEntries() {
        val state = FavoritesUiState(isLoading = true, entries = emptyList())
        assertFalse(state.isEmpty)
    }

    @Test
    fun isEmpty_false_whenEntriesPresentAndNotLoading() {
        val state = FavoritesUiState(isLoading = false, entries = listOf(entryFixture()))
        assertFalse(state.isEmpty)
    }

    @Test
    fun isEmpty_false_whileLoadingWithEntriesPresent() {
        val state = FavoritesUiState(isLoading = true, entries = listOf(entryFixture()))
        assertFalse(state.isEmpty)
    }

    @Test
    fun defaultState_isLoadingTrue_andNotEmpty() {
        val state = FavoritesUiState()
        assertTrue(state.isLoading)
        assertTrue(state.entries.isEmpty())
        assertFalse(state.isEmpty)
    }

    @Test
    fun favoriteEntryFixture_carriesBookmark() {
        val entry = entryFixture()
        assertTrue(entry.isDirectory)
        assertTrue(entry.item == null)
        assertTrue(entry.bookmark.label == "Documents")
    }
}
