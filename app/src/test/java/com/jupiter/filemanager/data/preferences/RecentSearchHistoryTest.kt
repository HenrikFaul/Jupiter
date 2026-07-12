package com.jupiter.filemanager.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchHistoryTest {

    @Test
    fun `new query is newest, case insensitive duplicates collapse, and history is bounded`() {
        val existing = listOf(
            "Budget Q2",
            "Project proposal",
            "Tax report",
            "Downloads",
            "Images",
            "Receipts",
            "Invoices",
            "Notes",
        )

        val updated = RecentSearchHistory.updated(existing, "  budget q2  ")

        assertEquals(
            listOf(
                "budget q2",
                "Project proposal",
                "Tax report",
                "Downloads",
                "Images",
                "Receipts",
                "Invoices",
                "Notes",
            ),
            updated,
        )
        assertEquals(RecentSearchHistory.MAX_ENTRIES, updated.size)

        val capped = RecentSearchHistory.updated(existing, "New search")
        assertEquals(RecentSearchHistory.MAX_ENTRIES, capped.size)
        assertEquals("New search", capped.first())
        assertEquals("Invoices", capped.last())
    }

    @Test
    fun `serialization round trips arbitrary local query text and ignores corrupt entries`() {
        val queries = listOf("invoice 2026", "c++ notes / \u00e1rv\u00edzt\u0171r\u0151", "family, photos")

        assertEquals(queries, RecentSearchHistory.decode(RecentSearchHistory.encode(queries)))
        assertEquals(emptyList<String>(), RecentSearchHistory.decode("!!!"))
    }
}
