package com.jupiter.filemanager.data.file

import android.content.Context
import com.jupiter.filemanager.core.util.extensionOf
import com.jupiter.filemanager.core.util.fileTypeFor
import com.jupiter.filemanager.core.util.mimeTypeFor
import com.jupiter.filemanager.domain.model.FileItem
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Low-level data source that maps the local file system into immutable domain
 * [FileItem] instances.
 *
 * All members are pure read operations that never throw: unreadable or
 * inaccessible entries are skipped or mapped with conservative defaults rather
 * than propagating [SecurityException]s or IO errors. This keeps callers in the
 * data layer (e.g. repositories) simple while remaining safe to invoke off the
 * main thread.
 */
@Singleton
class FileSystemDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Lists the immediate children of the directory at [path], mapping each
     * readable child to a [FileItem].
     *
     * Returns an empty list when the path does not exist, is not a directory, is
     * not readable, or when the platform refuses to enumerate it. Individual
     * entries that cannot be mapped are skipped rather than aborting the whole
     * listing.
     */
    fun listDirectory(path: String): List<FileItem> {
        val directory = File(path)

        val children: Array<File>? = try {
            if (!directory.isDirectory) return emptyList()
            directory.listFiles()
        } catch (_: SecurityException) {
            null
        }

        if (children.isNullOrEmpty()) return emptyList()

        return children.mapNotNull { child ->
            try {
                toFileItem(child)
            } catch (_: SecurityException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    /**
     * Maps a single [java.io.File] to a [FileItem], deriving its type, extension,
     * and MIME information via the shared [com.jupiter.filemanager.core.util]
     * helpers.
     *
     * For directories the child count is computed best-effort; if enumeration is
     * not permitted the count is reported as null. Permission and metadata reads
     * are individually guarded so a single failing accessor never aborts the
     * mapping.
     */
    fun toFileItem(file: File): FileItem {
        val name = file.name.ifEmpty { file.path }
        val isDirectory = safeBoolean { file.isDirectory }
        val extension = if (isDirectory) "" else extensionOf(name)
        val mimeType = if (isDirectory) null else mimeTypeFor(name)
        val childCount = if (isDirectory) safeChildCount(file) else null

        return FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else safeLong { file.length() },
            lastModified = safeLong { file.lastModified() },
            type = fileTypeFor(name, isDirectory),
            extension = extension,
            mimeType = mimeType,
            isHidden = safeBoolean { file.isHidden } || name.startsWith('.'),
            childCount = childCount,
            canRead = safeBoolean { file.canRead() },
            canWrite = safeBoolean { file.canWrite() },
        )
    }

    /**
     * Lazily walks the tree rooted at [rootPath] in depth-first, top-down order.
     *
     * The traversal is fully lazy (a cold [Sequence]) so callers can short-circuit
     * cheaply. To minimally guard against symlink loops and runaway descents, each
     * directory's canonical path is recorded and never re-entered, and the depth is
     * capped. Unreadable directories are skipped silently.
     */
    fun walkTopDown(rootPath: String): Sequence<File> = sequence {
        val root = File(rootPath)
        if (!root.exists()) return@sequence

        // Tracks canonical directory paths already descended into, so symlinks
        // that point back up the tree (or sideways into a visited subtree) do not
        // cause infinite recursion.
        val visited = HashSet<String>()
        val stack = ArrayDeque<DirFrame>()
        stack.addLast(DirFrame(root, depth = 0))

        while (stack.isNotEmpty()) {
            val frame = stack.removeLast()
            val file = frame.file

            yield(file)

            val isDir = safeBoolean { file.isDirectory }
            if (!isDir || frame.depth >= MAX_WALK_DEPTH) continue

            val canonical = canonicalKey(file)
            if (!visited.add(canonical)) continue

            val children: Array<File>? = try {
                file.listFiles()
            } catch (_: SecurityException) {
                null
            }
            if (children.isNullOrEmpty()) continue

            // Push in reverse so iteration order remains stable (first child first).
            for (index in children.indices.reversed()) {
                stack.addLast(DirFrame(children[index], frame.depth + 1))
            }
        }
    }

    /** Returns true when a file or directory exists at [path]. */
    fun exists(path: String): Boolean = try {
        File(path).exists()
    } catch (_: SecurityException) {
        false
    }

    // region Internals --------------------------------------------------------

    private data class DirFrame(val file: File, val depth: Int)

    /**
     * Best-effort count of the immediate children of [directory]. Returns null
     * when the directory cannot be enumerated (e.g. permission denied) so callers
     * can distinguish "empty" from "unknown".
     */
    private fun safeChildCount(directory: File): Int? = try {
        directory.list()?.size
    } catch (_: SecurityException) {
        null
    }

    /**
     * Resolves a stable identity key for symlink-loop detection, preferring the
     * canonical path and falling back to the absolute path when the canonical form
     * cannot be computed.
     */
    private fun canonicalKey(file: File): String = try {
        file.canonicalPath
    } catch (_: Exception) {
        file.absolutePath
    }

    private inline fun safeBoolean(block: () -> Boolean): Boolean = try {
        block()
    } catch (_: SecurityException) {
        false
    }

    private inline fun safeLong(block: () -> Long): Long = try {
        block()
    } catch (_: SecurityException) {
        0L
    }

    // endregion

    private companion object {
        /** Hard ceiling on traversal depth to bound pathological/looping trees. */
        const val MAX_WALK_DEPTH: Int = 64
    }
}
