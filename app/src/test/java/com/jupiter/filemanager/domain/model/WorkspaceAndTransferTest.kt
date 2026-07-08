package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceAndTransferTest {

    // ---- Workspace.itemCount ----

    @Test
    fun workspace_itemCount_emptyWhenNoPaths() {
        val ws = Workspace(id = "w1", name = "Empty", itemPaths = emptyList())
        assertEquals(0, ws.itemCount)
    }

    @Test
    fun workspace_itemCount_matchesPathsSize() {
        val paths = listOf("/a", "/b", "/c")
        val ws = Workspace(id = "w2", name = "Three", itemPaths = paths)
        assertEquals(paths.size, ws.itemCount)
        assertEquals(3, ws.itemCount)
    }

    @Test
    fun workspace_itemCount_countsDuplicatePathsToo() {
        val ws = Workspace(id = "w3", name = "Dups", itemPaths = listOf("/a", "/a", "/b"))
        assertEquals(3, ws.itemCount)
    }

    @Test
    fun workspace_defaults_areZero() {
        val ws = Workspace(id = "w4", name = "Defaults", itemPaths = listOf("/x"))
        assertEquals(0L, ws.totalBytes)
        assertEquals(0L, ws.lastModified)
        assertEquals(1, ws.itemCount)
    }

    // ---- TransferTask.fraction ----

    private fun task(size: Long, transferred: Long) = TransferTask(
        id = "t",
        fileName = "f.bin",
        sizeBytes = size,
        transferredBytes = transferred,
        status = TransferStatus.IN_PROGRESS,
        direction = TransferDirection.SEND,
    )

    @Test
    fun fraction_zeroWhenSizeIsZero() {
        assertEquals(0f, task(size = 0L, transferred = 50L).fraction, 0f)
    }

    @Test
    fun fraction_zeroWhenSizeNegative() {
        assertEquals(0f, task(size = -10L, transferred = 5L).fraction, 0f)
    }

    @Test
    fun fraction_midpoint() {
        assertEquals(0.5f, task(size = 100L, transferred = 50L).fraction, 1e-6f)
    }

    @Test
    fun fraction_zeroWhenNothingTransferred() {
        assertEquals(0f, task(size = 100L, transferred = 0L).fraction, 0f)
    }

    @Test
    fun fraction_oneWhenFullyTransferred() {
        assertEquals(1f, task(size = 100L, transferred = 100L).fraction, 0f)
    }

    @Test
    fun fraction_clampedToOneWhenOverTransferred() {
        assertEquals(1f, task(size = 100L, transferred = 250L).fraction, 0f)
    }

    @Test
    fun fraction_neverNegative_andNeverAboveOne() {
        val values = listOf(
            task(0L, 5L).fraction,
            task(-5L, 5L).fraction,
            task(100L, -20L).fraction,
            task(100L, 30L).fraction,
            task(100L, 500L).fraction,
        )
        values.forEach {
            assertTrue("fraction $it should be >= 0", it >= 0f)
            assertTrue("fraction $it should be <= 1", it <= 1f)
        }
    }

    @Test
    fun fraction_clampedToZeroWhenNegativeTransferred() {
        // transferred negative but size positive -> coerceIn lower bound 0f
        assertEquals(0f, task(size = 100L, transferred = -20L).fraction, 0f)
    }
}
