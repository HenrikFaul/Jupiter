package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.OperationState
import com.github.junrar.Junrar
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
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

    /**
     * Extracts the archive at [archivePath] into [destinationDir], dispatching by file
     * extension to the appropriate backend:
     *
     *  - **zip** — delegates to [extractZip] (JDK `java.util.zip`).
     *  - **tar / tar.gz / tgz / gz / bz2** — Apache Commons Compress
     *    (`org.apache.commons.compress.archivers` / `.compressors`).
     *  - **7z** — `org.apache.commons.compress.archivers.sevenz.SevenZFile`.
     *  - **rar** — `com.github.junrar.Junrar`.
     *
     * Each backend is guarded; an unsupported extension or any failure emits a terminal
     * [OperationState.FAILED] snapshot rather than throwing. Work runs on the injected
     * [IoDispatcher] and is cooperative with cancellation.
     */
    fun extractArchive(
        archivePath: String,
        destinationDir: String,
    ): Flow<FileOperationProgress> {
        val lower = archivePath.lowercase()
        return when {
            lower.endsWith(".zip") -> extractZip(archivePath, destinationDir)
            lower.endsWith(".tar.gz") || lower.endsWith(".tgz") ->
                extractCompressedTar(archivePath, destinationDir, Compression.GZIP)
            lower.endsWith(".tar.bz2") || lower.endsWith(".tbz2") || lower.endsWith(".tbz") ->
                extractCompressedTar(archivePath, destinationDir, Compression.BZIP2)
            lower.endsWith(".tar") ->
                extractCompressedTar(archivePath, destinationDir, Compression.NONE)
            lower.endsWith(".gz") -> extractSingleCompressed(archivePath, destinationDir, Compression.GZIP)
            lower.endsWith(".bz2") -> extractSingleCompressed(archivePath, destinationDir, Compression.BZIP2)
            lower.endsWith(".7z") -> extractSevenZ(archivePath, destinationDir)
            lower.endsWith(".rar") -> extractRar(archivePath, destinationDir)
            else -> unsupported(archivePath)
        }
    }

    // region Extended extraction backends -------------------------------------

    /** Compression wrapping applied to a tar stream (or a single compressed file). */
    private enum class Compression { NONE, GZIP, BZIP2 }

    /** Emits a single terminal FAILED snapshot for an unsupported archive type. */
    private fun unsupported(archivePath: String): Flow<FileOperationProgress> = flow {
        emit(
            failure(
                type = FileOperationType.EXTRACT,
                processedItems = 0,
                totalItems = 0,
                processedBytes = 0L,
                totalBytes = File(archivePath).length(),
                message = "Unsupported archive format: " + File(archivePath).name,
            ),
        )
    }.flowOn(dispatcher)

    /**
     * Extracts a (optionally gzip/bzip2-compressed) TAR archive via Commons Compress,
     * recreating the stored directory structure with Zip-Slip protection.
     */
    private fun extractCompressedTar(
        archivePath: String,
        destinationDir: String,
        compression: Compression,
    ): Flow<FileOperationProgress> = flow {
        val source = File(archivePath)
        val targetRoot = File(destinationDir)
        val totalBytes = source.length()

        emitRunning(source.name, totalBytes)

        if (!source.isFile) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = 0,
                    totalItems = 0,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    message = "Archive not found: " + archivePath,
                ),
            )
            return@flow
        }

        targetRoot.mkdirs()
        val canonicalRoot = targetRoot.canonicalPath
        var processedItems = 0
        var processedBytes = 0L

        try {
            BufferedInputStream(FileInputStream(source)).use { raw ->
                val decompressed: InputStream = when (compression) {
                    Compression.NONE -> raw
                    Compression.GZIP -> GzipCompressorInputStream(raw)
                    Compression.BZIP2 -> BZip2CompressorInputStream(raw)
                }
                TarArchiveInputStream(decompressed).use { tar ->
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val entry: ArchiveEntry = tar.nextEntry ?: break
                        processedBytes = writeArchiveEntry(
                            input = tar,
                            entry = entry,
                            targetRoot = targetRoot,
                            canonicalRoot = canonicalRoot,
                            processedItems = processedItems,
                            startBytes = processedBytes,
                            totalBytes = totalBytes,
                        )
                        processedItems += 1
                    }
                }
            }

            emitCompleted(targetRoot.name, processedItems, processedBytes, totalBytes)
        } catch (security: SecurityException) {
            emitExtractFailure(processedItems, processedBytes, totalBytes, security.message)
        } catch (io: IOException) {
            emitExtractFailure(processedItems, processedBytes, totalBytes, io.message)
        }
    }.flowOn(dispatcher)

    /**
     * Extracts a single-file compressor stream (.gz / .bz2 with no tar inside) by
     * stripping the compression suffix from the file name.
     */
    private fun extractSingleCompressed(
        archivePath: String,
        destinationDir: String,
        compression: Compression,
    ): Flow<FileOperationProgress> = flow {
        val source = File(archivePath)
        val targetRoot = File(destinationDir)
        val totalBytes = source.length()

        emitRunning(source.name, totalBytes)

        if (!source.isFile) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = 0,
                    totalItems = 0,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    message = "Archive not found: " + archivePath,
                ),
            )
            return@flow
        }

        targetRoot.mkdirs()
        val canonicalRoot = targetRoot.canonicalPath
        val outName = source.name.substringBeforeLast('.')
            .ifBlank { source.name + ".out" }
        val outFile = resolveSafeEntry(targetRoot, canonicalRoot, outName)
        var processedBytes = 0L

        try {
            BufferedInputStream(FileInputStream(source)).use { raw ->
                val decompressed: InputStream = when (compression) {
                    Compression.GZIP -> GzipCompressorInputStream(raw)
                    Compression.BZIP2 -> BZip2CompressorInputStream(raw)
                    Compression.NONE -> raw
                }
                outFile.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(outFile)).use { output ->
                    processedBytes = copyEntry(
                        input = decompressed,
                        output = output,
                        currentName = outFile.name,
                        startBytes = 0L,
                        totalBytes = totalBytes,
                        processedItems = 0,
                    )
                }
            }

            emitCompleted(targetRoot.name, 1, processedBytes, totalBytes)
        } catch (security: SecurityException) {
            emitExtractFailure(0, processedBytes, totalBytes, security.message)
        } catch (io: IOException) {
            emitExtractFailure(0, processedBytes, totalBytes, io.message)
        }
    }.flowOn(dispatcher)

    /** Extracts a 7z archive via [SevenZFile], with Zip-Slip protection. */
    private fun extractSevenZ(
        archivePath: String,
        destinationDir: String,
    ): Flow<FileOperationProgress> = flow {
        val source = File(archivePath)
        val targetRoot = File(destinationDir)
        val totalBytes = source.length()

        emitRunning(source.name, totalBytes)

        if (!source.isFile) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = 0,
                    totalItems = 0,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    message = "Archive not found: " + archivePath,
                ),
            )
            return@flow
        }

        targetRoot.mkdirs()
        val canonicalRoot = targetRoot.canonicalPath
        var processedItems = 0
        var processedBytes = 0L

        try {
            SevenZFile(source).use { sevenZ ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = sevenZ.nextEntry ?: break
                    val outFile = resolveSafeEntry(targetRoot, canonicalRoot, entry.name)

                    if (entry.isDirectory) {
                        outFile.mkdirs()
                        processedItems += 1
                        emitEntryProgress(outFile.name, processedItems, processedBytes, totalBytes)
                        continue
                    }

                    outFile.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(outFile)).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = sevenZ.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            processedBytes += read
                            emitEntryProgress(outFile.name, processedItems, processedBytes, totalBytes)
                        }
                    }
                    processedItems += 1
                }
            }

            emitCompleted(targetRoot.name, processedItems, processedBytes, totalBytes)
        } catch (security: SecurityException) {
            emitExtractFailure(processedItems, processedBytes, totalBytes, security.message)
        } catch (io: IOException) {
            emitExtractFailure(processedItems, processedBytes, totalBytes, io.message)
        }
    }.flowOn(dispatcher)

    /**
     * Extracts a RAR archive via [Junrar]. Junrar performs the full extraction into the
     * destination directory in one call; progress is reported as a single running phase
     * followed by a terminal snapshot.
     */
    private fun extractRar(
        archivePath: String,
        destinationDir: String,
    ): Flow<FileOperationProgress> = flow {
        val source = File(archivePath)
        val targetRoot = File(destinationDir)
        val totalBytes = source.length()

        emitRunning(source.name, totalBytes)

        if (!source.isFile) {
            emit(
                failure(
                    type = FileOperationType.EXTRACT,
                    processedItems = 0,
                    totalItems = 0,
                    processedBytes = 0L,
                    totalBytes = totalBytes,
                    message = "Archive not found: " + archivePath,
                ),
            )
            return@flow
        }

        try {
            targetRoot.mkdirs()
            currentCoroutineContext().ensureActive()
            Junrar.extract(source, targetRoot)
            emitCompleted(targetRoot.name, 0, totalBytes, totalBytes)
        } catch (security: SecurityException) {
            emitExtractFailure(0, 0L, totalBytes, security.message)
        } catch (t: Throwable) {
            // Junrar can throw its own checked RarException; treat any failure uniformly.
            emitExtractFailure(0, 0L, totalBytes, t.message)
        }
    }.flowOn(dispatcher)

    /**
     * Writes a single Commons Compress [entry] into [targetRoot], guarding against
     * Zip-Slip, and returns the running processed-bytes total.
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.writeArchiveEntry(
        input: ArchiveInputStream,
        entry: ArchiveEntry,
        targetRoot: File,
        canonicalRoot: String,
        processedItems: Int,
        startBytes: Long,
        totalBytes: Long,
    ): Long {
        val outFile = resolveSafeEntry(targetRoot, canonicalRoot, entry.name)
        if (entry.isDirectory) {
            outFile.mkdirs()
            emitEntryProgress(outFile.name, processedItems + 1, startBytes, totalBytes)
            return startBytes
        }
        outFile.parentFile?.mkdirs()
        var written = startBytes
        BufferedOutputStream(FileOutputStream(outFile)).use { output ->
            written = copyEntry(
                input = input,
                output = output,
                currentName = outFile.name,
                startBytes = startBytes,
                totalBytes = totalBytes,
                processedItems = processedItems,
            )
        }
        return written
    }

    /** Emits the initial RUNNING snapshot for an extended-extraction backend. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.emitRunning(
        name: String,
        totalBytes: Long,
    ) {
        emit(
            FileOperationProgress(
                type = FileOperationType.EXTRACT,
                state = OperationState.RUNNING,
                processedItems = 0,
                totalItems = 0,
                processedBytes = 0L,
                totalBytes = totalBytes,
                currentFileName = name,
            ),
        )
    }

    /** Emits a per-entry RUNNING progress snapshot. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.emitEntryProgress(
        name: String,
        processedItems: Int,
        processedBytes: Long,
        totalBytes: Long,
    ) {
        emit(
            FileOperationProgress(
                type = FileOperationType.EXTRACT,
                state = OperationState.RUNNING,
                processedItems = processedItems,
                totalItems = 0,
                processedBytes = processedBytes,
                totalBytes = totalBytes,
                currentFileName = name,
            ),
        )
    }

    /** Emits the terminal COMPLETED snapshot for an extended-extraction backend. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.emitCompleted(
        name: String,
        processedItems: Int,
        processedBytes: Long,
        totalBytes: Long,
    ) {
        emit(
            FileOperationProgress(
                type = FileOperationType.EXTRACT,
                state = OperationState.COMPLETED,
                processedItems = processedItems,
                totalItems = processedItems,
                processedBytes = processedBytes,
                totalBytes = totalBytes,
                currentFileName = name,
            ),
        )
    }

    /** Emits the terminal FAILED snapshot for an extended-extraction backend. */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.emitExtractFailure(
        processedItems: Int,
        processedBytes: Long,
        totalBytes: Long,
        message: String?,
    ) {
        emit(
            failure(
                type = FileOperationType.EXTRACT,
                processedItems = processedItems,
                totalItems = processedItems,
                processedBytes = processedBytes,
                totalBytes = totalBytes,
                message = message ?: "Failed to extract archive.",
            ),
        )
    }

    // endregion

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
