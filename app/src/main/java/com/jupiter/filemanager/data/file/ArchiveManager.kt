package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.OperationState
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.currentCoroutineContext

/**
 * Handles ZIP archive creation and extraction using the JDK [java.util.zip] APIs.
 *
 * Both operations expose a cold [Flow] of [FileOperationProgress] so the UI can
 * render incremental progress. Work runs on the injected [IoDispatcher]; the flow
 * is cooperative with cancellation (collecting coroutine cancellation aborts the
 * stream and the partially written destination is cleaned up).
 */
@Singleton
class ArchiveManager @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * Compresses [items] (files and/or directories, recursively) into a single ZIP
     * archive written to [destinationZipPath].
     *
     * The flow first emits a [OperationState.RUNNING] snapshot, then a snapshot per
     * file as bytes are streamed, and finally a terminal [OperationState.COMPLETED]
     * (or [OperationState.FAILED] on error). If the collector cancels mid-way the
     * incomplete archive is deleted.
     */
    fun createZip(
        items: List<FileItem>,
        destinationZipPath: String,
    ): Flow<FileOperationProgress> = flow {
        // Resolve the concrete set of regular files to compress, preserving the
        // archive-relative path for each so directory structure is retained.
        val entries: List<ZipSource> = items.flatMap { item ->
            collectZipSources(File(item.path))
        }

        val totalBytes: Long = entries.sumOf { it.file.length() }
        val totalItems: Int = entries.size

        emit(
            FileOperationProgress(
                type = FileOperationType.COMPRESS,
                state = OperationState.RUNNING,
                processedItems = 0,
                totalItems = totalItems,
                processedBytes = 0L,
                totalBytes = totalBytes,
                currentFileName = "",
            ),
        )

        val destination = File(destinationZipPath)
        destination.parentFile?.mkdirs()

        var processedItems = 0
        var processedBytes = 0L
        var completed = false

        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { zip ->
                for (source in entries) {
                    currentCoroutineContext().ensureActive()

                    val entry = ZipEntry(source.entryName).apply {
                        time = source.file.lastModified()
                    }
                    zip.putNextEntry(entry)

                    BufferedInputStream(FileInputStream(source.file)).use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            zip.write(buffer, 0, read)
                            processedBytes += read

                            emit(
                                FileOperationProgress(
                                    type = FileOperationType.COMPRESS,
                                    state = OperationState.RUNNING,
                                    processedItems = processedItems,
                                    totalItems = totalItems,
                                    processedBytes = processedBytes,
                                    totalBytes = totalBytes,
                                    currentFileName = source.file.name,
                                ),
                            )
                        }
                    }

                    zip.closeEntry()
                    processedItems += 1
                }
            }
            completed = true

            emit(
                FileOperationProgress(
                    type = FileOperationType.COMPRESS,
                    state = OperationState.COMPLETED,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    currentFileName = destination.name,
                ),
            )
        } catch (security: SecurityException) {
            emit(
                failure(
                    type = FileOperationType.COMPRESS,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    message = security.message ?: "Access denied while creating archive.",
                ),
            )
        } catch (io: IOException) {
            emit(
                failure(
                    type = FileOperationType.COMPRESS,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    message = io.message ?: "Failed to create archive.",
                ),
            )
        } finally {
            // Remove a half-written archive on cancellation or failure.
            if (!completed) {
                destination.delete()
            }
        }
    }.flowOn(dispatcher)

    /**
     * Extracts the ZIP archive at [zipPath] into [destinationDir], recreating the
     * stored directory structure.
     *
     * Entry names are sanitized against path traversal ("Zip Slip"): any entry that
     * would resolve outside [destinationDir] is rejected as an [IOException]. The
     * flow emits running progress per entry and a terminal completed/failed snapshot.
     */
    fun extractZip(
        zipPath: String,
        destinationDir: String,
    ): Flow<FileOperationProgress> = flow {
        val source = File(zipPath)
        val targetRoot = File(destinationDir)

        val totalBytes: Long = source.length()

        emit(
            FileOperationProgress(
                type = FileOperationType.EXTRACT,
                state = OperationState.RUNNING,
                processedItems = 0,
                totalItems = 0,
                processedBytes = 0L,
                totalBytes = totalBytes,
                currentFileName = source.name,
            ),
        )

        if (!source.isFile) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = 0,
                    totalItems = 0,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    message = "Archive not found: " + zipPath,
                ),
            )
            return@flow
        }

        targetRoot.mkdirs()
        val canonicalRoot: String = targetRoot.canonicalPath

        var processedItems = 0
        var processedBytes = 0L

        try {
            ZipInputStream(BufferedInputStream(FileInputStream(source))).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry: ZipEntry = zip.nextEntry ?: break

                    val outFile = resolveSafeEntry(targetRoot, canonicalRoot, entry.name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        zip.closeEntry()
                        processedItems += 1
                        emit(
                            FileOperationProgress(
                                type = FileOperationType.EXTRACT,
                                state = OperationState.RUNNING,
                                processedItems = processedItems,
                                totalItems = 0,
                                processedBytes = processedBytes,
                                totalBytes = totalBytes,
                                currentFileName = outFile.name,
                            ),
                        )
                        continue
                    }

                    outFile.parentFile?.mkdirs()

                    BufferedOutputStream(FileOutputStream(outFile)).use { output ->
                        processedBytes += copyEntry(
                            input = zip,
                            output = output,
                            currentName = outFile.name,
                            startBytes = processedBytes,
                            totalBytes = totalBytes,
                            processedItems = processedItems,
                        )
                    }

                    if (entry.time > 0L) {
                        outFile.setLastModified(entry.time)
                    }

                    zip.closeEntry()
                    processedItems += 1
                }
            }

            emit(
                FileOperationProgress(
                    type = FileOperationType.EXTRACT,
                    state = OperationState.COMPLETED,
                    processedItems = processedItems,
                    totalItems = processedItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    currentFileName = targetRoot.name,
                ),
            )
        } catch (security: SecurityException) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = processedItems,
                    totalItems = processedItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    message = security.message ?: "Access denied while extracting archive.",
                ),
            )
        } catch (io: IOException) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = processedItems,
                    totalItems = processedItems,
                    processedBytes = processedBytes,
                    totalBytes = totalBytes,
                    message = io.message ?: "Failed to extract archive.",
                ),
            )
        }
    }.flowOn(dispatcher)

    // region Internals --------------------------------------------------------

    /** A single regular file to be added to an archive under [entryName]. */
    private data class ZipSource(val file: File, val entryName: String)

    /**
     * Streams the current ZIP entry's content into [output], emitting incremental
     * progress, and returns the number of bytes written for this entry.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.copyEntry(
        input: InputStream,
        output: BufferedOutputStream,
        currentName: String,
        startBytes: Long,
        totalBytes: Long,
        processedItems: Int,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            output.write(buffer, 0, read)
            written += read

            emit(
                FileOperationProgress(
                    type = FileOperationType.EXTRACT,
                    state = OperationState.RUNNING,
                    processedItems = processedItems,
                    totalItems = 0,
                    processedBytes = startBytes + written,
                    totalBytes = totalBytes,
                    currentFileName = currentName,
                ),
            )
        }
        return written
    }

    /**
     * Recursively expands [root] into the flat list of regular files to compress,
     * assigning each a forward-slash archive path relative to [root]'s parent so the
     * top-level name (file or directory) is preserved inside the archive.
     */
    private fun collectZipSources(root: File): List<ZipSource> {
        if (!root.exists()) return emptyList()

        val basePrefix = root.parentFile?.absolutePath?.let { it + File.separator } ?: ""
        val result = ArrayList<ZipSource>()

        fun relativeName(file: File): String =
            file.absolutePath.removePrefix(basePrefix).replace(File.separatorChar, '/')

        val stack = ArrayDeque<File>()
        stack.addLast(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val isDir = try {
                current.isDirectory
            } catch (_: SecurityException) {
                false
            }

            if (isDir) {
                val children: Array<File>? = try {
                    current.listFiles()
                } catch (_: SecurityException) {
                    null
                }
                if (children.isNullOrEmpty()) {
                    // Preserve empty directories as explicit directory entries.
                    result.add(ZipSource(current, relativeName(current).trimEnd('/') + "/"))
                } else {
                    for (index in children.indices.reversed()) {
                        stack.addLast(children[index])
                    }
                }
            } else {
                val readable = try {
                    current.canRead()
                } catch (_: SecurityException) {
                    false
                }
                if (readable) {
                    result.add(ZipSource(current, relativeName(current)))
                }
            }
        }
        return result
    }

    /**
     * Resolves [entryName] against [targetRoot] and guards against Zip-Slip path
     * traversal by ensuring the resolved canonical path stays within [canonicalRoot].
     */
    private fun resolveSafeEntry(
        targetRoot: File,
        canonicalRoot: String,
        entryName: String,
    ): File {
        val resolved = File(targetRoot, entryName)
        val canonical = resolved.canonicalPath
        if (canonical != canonicalRoot &&
            !canonical.startsWith(canonicalRoot + File.separator)
        ) {
            throw IOException("Blocked malicious archive entry: " + entryName)
        }
        return resolved
    }

    /** Builds a terminal [OperationState.FAILED] progress snapshot. */
    private fun failure(
        type: FileOperationType,
        processedItems: Int,
        totalItems: Int,
        processedBytes: Long,
        totalBytes: Long,
        message: String,
    ): FileOperationProgress = FileOperationProgress(
        type = type,
        state = OperationState.FAILED,
        processedItems = processedItems,
        totalItems = totalItems,
        processedBytes = processedBytes,
        totalBytes = totalBytes,
        currentFileName = "",
        errorMessage = message,
    )

    // endregion

    private companion object {
        /** Streaming buffer size in bytes for read/write loops. */
        const val DEFAULT_BUFFER_SIZE: Int = 64 * 1024
    }
}
