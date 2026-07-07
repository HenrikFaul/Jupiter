package com.jupiter.filemanager.data.index

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [ChangeDebounce] — the reason the duplicate alert previously never
 * fired for a quickly-downloaded file: MediaStore fires several events (<1.5 s apart) while
 * a download is written, and a path-only sliding debounce swallowed the FINAL event too, so
 * the completed bytes were never hashed. Identity keying must let the completion through.
 */
class ChangeDebounceTest {

    private companion object {
        const val WINDOW = 1_500L
    }

    /** MediaStore chatter about the SAME bytes within the window is coalesced. */
    @Test
    fun `same identity within window is suppressed`() {
        assertTrue(ChangeDebounce.shouldProcess(lastHandledAtMs = null, nowMs = 0L, windowMs = WINDOW))
        assertFalse(ChangeDebounce.shouldProcess(lastHandledAtMs = 0L, nowMs = 800L, windowMs = WINDOW))
    }

    /** After the window the same identity is processed again (fresh look at the file). */
    @Test
    fun `same identity after window is processed`() {
        assertTrue(ChangeDebounce.shouldProcess(lastHandledAtMs = 0L, nowMs = 1_500L, windowMs = WINDOW))
    }

    /**
     * THE bug: a download in progress emits (path, 0 bytes) then (path, final bytes) less
     * than a window apart. The keys differ, so the second event has no prior timestamp and
     * MUST be processed — the completed content gets hashed and the alert can fire.
     */
    @Test
    fun `completion event has a different key so it is never swallowed by earlier chatter`() {
        val pendingKey = ChangeDebounce.key("/dl/img.jpg", sizeBytes = 0L, lastModified = 100L)
        val finalKey = ChangeDebounce.key("/dl/img.jpg", sizeBytes = 481_204L, lastModified = 900L)
        assertNotEquals("in-flight and completed states must be distinct events", pendingKey, finalKey)
        // A key never seen before (null timestamp) is always processed, however recent the chatter.
        assertTrue(ChangeDebounce.shouldProcess(lastHandledAtMs = null, nowMs = 300L, windowMs = WINDOW))
    }

    /** Identity keys are exact: same path+size+mtime map to the same key. */
    @Test
    fun `identical identity maps to identical key`() {
        assertEquals(
            ChangeDebounce.key("/a/b.jpg", 10L, 5L),
            ChangeDebounce.key("/a/b.jpg", 10L, 5L),
        )
    }
}
