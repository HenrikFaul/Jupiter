package com.jupiter.filemanager.feature.categories

import com.jupiter.filemanager.domain.model.FileItem
import java.io.File
import java.util.Locale

/** Pure safety policy around the repository-backed Photos move workflow. */
internal object PhotoMovePolicy {

    /** Null means the request may be handed to FileRepository.move for atomic preflight. */
    fun validationError(
        items: List<FileItem>,
        destinationPath: String,
        rootPath: String,
    ): String? {
        if (items.isEmpty()) return "Select at least one photo to move."
        if (destinationPath.isBlank()) return "Choose a destination folder."
        if (!isWithinRoot(destinationPath, rootPath)) return "The destination is outside app storage."
        if (items.any { it.path.startsWith("content://", ignoreCase = true) }) {
            return "A selected photo is available through MediaStore only and cannot be moved by path."
        }
        if (items.any { samePath(it.parentPath.orEmpty(), destinationPath) }) {
            return "A selected photo is already in this folder. Choose another destination."
        }
        return null
    }

    /** Old paths remain suppressed for this VM lifetime so stale MediaStore rows cannot return. */
    fun withoutSuppressedPaths(
        items: List<FileItem>,
        suppressedPaths: Set<String>,
    ): List<FileItem> {
        if (items.isEmpty() || suppressedPaths.isEmpty()) return items
        val normalizedSuppressed = suppressedPaths.asSequence().map(::normalized).toHashSet()
        return items.filterNot { normalized(it.path) in normalizedSuppressed }
    }

    fun isWithinRoot(path: String, rootPath: String): Boolean {
        if (path.isBlank() || rootPath.isBlank()) return false
        // Android storage paths are case-sensitive. Keep containment strict;
        // same-path/suppression comparisons below remain case-folded so stale
        // vendor MediaStore casing cannot resurrect a moved item.
        val normalizedPath = normalizedExact(path)
        val normalizedRoot = normalizedExact(rootPath).trimEnd('/')
        return normalizedPath == normalizedRoot || normalizedPath.startsWith("$normalizedRoot/")
    }

    fun samePath(first: String, second: String): Boolean =
        first.isNotBlank() && second.isNotBlank() && normalized(first) == normalized(second)

    fun parentWithinRoot(path: String, rootPath: String): String? {
        if (!isWithinRoot(path, rootPath) || samePath(path, rootPath)) return null
        val parent = File(path).parent ?: return null
        return parent.takeIf { isWithinRoot(it, rootPath) }
    }

    private fun normalized(path: String): String = path
        .replace('\\', '/')
        .trimEnd('/')
        .lowercase(Locale.ROOT)

    private fun normalizedExact(path: String): String = path
        .replace('\\', '/')
        .trimEnd('/')
}
