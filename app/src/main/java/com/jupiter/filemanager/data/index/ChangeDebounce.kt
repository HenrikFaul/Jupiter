package com.jupiter.filemanager.data.index

/**
 * Pure debounce decision for MediaStore change events, keyed by file IDENTITY
 * (path + size + mtime), not by path alone.
 *
 * MediaStore fires several times while a download is being written (insert, progress
 * updates, final commit), usually less than a second apart. A path-only sliding debounce
 * swallowed every follow-up event — INCLUDING the final one carrying the completed bytes —
 * so a quickly-downloaded duplicate was hashed (if at all) from a half-written file and the
 * "you already have this" alert never fired. Keying by identity means chatter about the
 * SAME bytes is still coalesced, but the completion event (different size/mtime) is always
 * processed.
 */
object ChangeDebounce {

    /** Identity key for one observed state of a file. */
    fun key(path: String, sizeBytes: Long, lastModified: Long): String =
        "$path|$sizeBytes|$lastModified"

    /**
     * @param lastHandledAtMs when this exact identity was last processed, or null if never.
     * @return true when the event should be PROCESSED (never seen, or outside the window).
     */
    fun shouldProcess(lastHandledAtMs: Long?, nowMs: Long, windowMs: Long): Boolean =
        lastHandledAtMs == null || nowMs - lastHandledAtMs >= windowMs
}
