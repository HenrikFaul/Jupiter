package com.jupiter.filemanager.data.index

/**
 * Pure path arithmetic for reflecting an in-app move/rename in the index WITHOUT
 * dropping (and later re-scanning) a directory's whole subtree.
 *
 * When a directory is renamed or moved, every descendant row's path must be
 * rewritten from the old prefix to the new one — preserving each row's already
 * computed content hash — rather than deleting the subtree and re-indexing only the
 * new root (which loses every descendant until the next full scan). These functions
 * are deliberately dependency-free so they can be unit-tested on the JVM.
 */
object IndexPathRewrite {

    /**
     * Returns the new path for [path] when it is [oldPrefix] itself or a descendant
     * of it, moved under [newPrefix]; returns null when [path] is unaffected.
     *
     * Matching is on whole path segments (a trailing `/` is appended for the
     * descendant test) so a sibling that merely shares a textual prefix — e.g.
     * `/a/photos` vs `/a/photos_2024` — is never rewritten.
     */
    fun rewrite(oldPrefix: String, newPrefix: String, path: String): String? {
        val op = oldPrefix.trimEnd('/')
        val np = newPrefix.trimEnd('/')
        if (op.isEmpty()) return null
        return when {
            path == op -> np
            path.startsWith("$op/") -> np + path.substring(op.length)
            else -> null
        }
    }

    /** Parent directory of [path], or null when it has no parent. Matches FileItem.parentPath. */
    fun parentOf(path: String): String? =
        path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { null }

    /** Leaf name of [path]. */
    fun nameOf(path: String): String = path.trimEnd('/').substringAfterLast('/')

    /**
     * True when an already-indexed file identity is unchanged, so its cached content
     * hash may be safely preserved across a re-index. Directories never carry a hash.
     *
     * Mtimes are compared at SECOND precision: MediaStore reports whole seconds
     * (`DATE_MODIFIED` × 1000) while a filesystem stat reports raw millis, so the SAME
     * untouched file arrives with two "different" mtimes depending on which enumerator
     * indexed it. Exact equality treated that rounding as a modification and silently
     * WIPED the cached content/perceptual/structural fingerprints on every re-index —
     * forcing whole-library re-hashing/re-decoding. A real edit always moves the mtime
     * by at least a second in practice, and the size check backstops the rest.
     */
    fun identityUnchanged(
        isDirectory: Boolean,
        oldSize: Long,
        oldMtime: Long,
        newSize: Long,
        newMtime: Long,
    ): Boolean = !isDirectory && oldSize == newSize && oldMtime / 1000 == newMtime / 1000
}
