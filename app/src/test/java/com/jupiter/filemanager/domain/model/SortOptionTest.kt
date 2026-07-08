package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortOptionTest {

    @Test
    fun defaultField_isName() {
        assertEquals(SortField.NAME, SortOption().field)
    }

    @Test
    fun defaultDirection_isAscending() {
        assertEquals(SortDirection.ASCENDING, SortOption().direction)
    }

    @Test
    fun defaultFoldersFirst_isTrue() {
        assertTrue(SortOption().foldersFirst)
    }

    @Test
    fun copy_overridesField() {
        val original = SortOption()
        val copy = original.copy(field = SortField.SIZE)
        assertEquals(SortField.SIZE, copy.field)
        assertEquals(original.direction, copy.direction)
        assertEquals(original.foldersFirst, copy.foldersFirst)
        assertNotEquals(original, copy)
    }

    @Test
    fun copy_overridesDirection() {
        val copy = SortOption().copy(direction = SortDirection.DESCENDING)
        assertEquals(SortDirection.DESCENDING, copy.direction)
        assertEquals(SortField.NAME, copy.field)
    }

    @Test
    fun copy_overridesFoldersFirst() {
        val copy = SortOption().copy(foldersFirst = false)
        assertEquals(false, copy.foldersFirst)
    }

    @Test
    fun copy_withNoArgs_equalsOriginal() {
        val original = SortOption(field = SortField.TYPE, direction = SortDirection.DESCENDING, foldersFirst = false)
        assertEquals(original, original.copy())
    }

    @Test
    fun sortField_hasAllExpectedValues() {
        val values = SortField.values().toList()
        assertEquals(4, values.size)
        assertTrue(values.contains(SortField.NAME))
        assertTrue(values.contains(SortField.SIZE))
        assertTrue(values.contains(SortField.DATE_MODIFIED))
        assertTrue(values.contains(SortField.TYPE))
    }

    @Test
    fun sortDirection_hasAllExpectedValues() {
        val values = SortDirection.values().toList()
        assertEquals(2, values.size)
        assertTrue(values.contains(SortDirection.ASCENDING))
        assertTrue(values.contains(SortDirection.DESCENDING))
    }

    @Test
    fun sortField_valueOf_roundTrips() {
        assertEquals(SortField.SIZE, SortField.valueOf("SIZE"))
        assertEquals(SortField.DATE_MODIFIED, SortField.valueOf("DATE_MODIFIED"))
    }

    @Test
    fun sortDirection_valueOf_roundTrips() {
        assertEquals(SortDirection.DESCENDING, SortDirection.valueOf("DESCENDING"))
    }

    @Test
    fun explicitConstruction_retainsValues() {
        val option = SortOption(SortField.DATE_MODIFIED, SortDirection.DESCENDING, false)
        assertEquals(SortField.DATE_MODIFIED, option.field)
        assertEquals(SortDirection.DESCENDING, option.direction)
        assertEquals(false, option.foldersFirst)
    }
}
