package com.jupiter.filemanager.data.index

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class PerceptualHashBackfillWorkerTest {

    @Test
    fun retryableRowsConsumeTheVisitedRowBudget() = runTest {
        val backlog = mutableListOf("001", "002", "003", "004", "005")
        val attempted = mutableListOf<String>()

        val result = runPerceptualBackfillPages<String>(
            initialBatch = backlog.toList(),
            batchSize = 10,
            maxVisitedRows = 3,
            loadAfter = { afterPath, limit ->
                backlog.filter { it > afterPath }.take(limit)
            },
            compute = { path ->
                attempted += path
                null // transient decode/provider failure: row stays in the backlog
            },
            persistBatch = { error("retryable rows must not be persisted") },
            countRemaining = { backlog.size },
        )

        assertEquals(listOf("001", "002", "003"), attempted)
        assertEquals(3, result.visitedRows)
        assertEquals(0, result.persistedRows)
        assertEquals(PerceptualBackfillLoopOutcome.Retry, result.outcome)
    }

    @Test
    fun finalFullBacklogCheckFindsARowInsertedBehindTheCursor() = runTest {
        val backlog = mutableListOf("middle")
        val attempted = mutableListOf<String>()
        var insertedBehindCursor = false

        val result = runPerceptualBackfillPages<String>(
            initialBatch = backlog.toList(),
            batchSize = 10,
            maxVisitedRows = 100,
            loadAfter = { afterPath, limit ->
                if (!insertedBehindCursor) {
                    // Models a concurrent insert or rename after the cursor has advanced. A pure
                    // keyset query cannot see this row because it sorts before `middle`.
                    backlog += "alpha"
                    insertedBehindCursor = true
                }
                backlog.filter { it > afterPath }.sorted().take(limit)
            },
            compute = { path ->
                attempted += path
                path
            },
            persistBatch = { updates ->
                backlog.removeAll(updates.toSet())
                true
            },
            countRemaining = { backlog.size },
        )

        assertEquals(listOf("middle"), attempted)
        assertEquals(listOf("alpha"), backlog)
        assertEquals(PerceptualBackfillLoopOutcome.Retry, result.outcome)
    }

    @Test
    fun emptyAuthoritativeBacklogCompletesSuccessfully() = runTest {
        val backlog = mutableListOf("only")

        val result = runPerceptualBackfillPages<String>(
            initialBatch = backlog.toList(),
            batchSize = 10,
            maxVisitedRows = 100,
            loadAfter = { afterPath, limit ->
                backlog.filter { it > afterPath }.take(limit)
            },
            compute = { path -> path },
            persistBatch = { updates ->
                backlog.removeAll(updates.toSet())
                true
            },
            countRemaining = { backlog.size },
        )

        assertEquals(1, result.visitedRows)
        assertEquals(1, result.persistedRows)
        assertEquals(PerceptualBackfillLoopOutcome.Success, result.outcome)
    }

    @Test
    fun computedUpdatesArePersistedOncePerPageAndBatchFailureRetriesTheWholePage() = runTest {
        val backlog = mutableListOf("001", "002")
        val persistedPages = mutableListOf<List<String>>()

        val result = runPerceptualBackfillPages<String>(
            initialBatch = backlog.toList(),
            batchSize = 100,
            maxVisitedRows = 100,
            loadAfter = { _, _ -> emptyList() },
            compute = { path -> path },
            persistBatch = { updates ->
                persistedPages += updates
                false // transaction failed: neither row may be counted as durable
            },
            countRemaining = { backlog.size },
        )

        assertEquals(listOf(listOf("001", "002")), persistedPages)
        assertEquals(2, result.visitedRows)
        assertEquals(0, result.persistedRows)
        assertEquals(PerceptualBackfillLoopOutcome.Retry, result.outcome)
    }

    @Test
    fun twoHundredAndFiveRowsPersistAsOneTransactionPerDatabasePage() = runTest {
        val backlog = (0 until 205).map { it.toString().padStart(3, '0') }.toMutableList()
        val persistedPageSizes = mutableListOf<Int>()

        val result = runPerceptualBackfillPages<String>(
            initialBatch = backlog.take(100),
            batchSize = 100,
            maxVisitedRows = 1_000,
            loadAfter = { afterPath, limit ->
                backlog.filter { it > afterPath }.sorted().take(limit)
            },
            compute = { path -> path },
            persistBatch = { updates ->
                persistedPageSizes += updates.size
                backlog.removeAll(updates.toSet())
                true
            },
            countRemaining = { backlog.size },
        )

        assertEquals(listOf(100, 100, 5), persistedPageSizes)
        assertEquals(205, result.visitedRows)
        assertEquals(205, result.persistedRows)
        assertEquals(PerceptualBackfillLoopOutcome.Success, result.outcome)
    }
}
