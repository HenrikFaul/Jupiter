package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.core.result.AppError
import com.jupiter.filemanager.core.result.AppResult
import com.jupiter.filemanager.di.IoDispatcher
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.FileOperationType
import com.jupiter.filemanager.domain.model.OperationState
import com.jupiter.filemanager.domain.repository.FileIndexRepository
import com.jupiter.filemanager.domain.repository.TrashRepository
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Performs long-running, recursive file operations (copy / move / delete) off the main thread.
 *
 * Copy and move are exposed as cold [Flow]s of [FileOperationProgress] so the UI can render
 * per-item and per-byte progress. Both honour cooperative cancellation: the producer periodically
 * checks [kotlin.coroutines.CoroutineContext.isActive] and stops promptly, emitting a terminal
 * [OperationState.CANCELLED] snapshot. Delete is a one-shot suspend returning an [AppResult].
 */
@Singleton
class FileOperationsManager @Inject constructor(
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
    private val trashRepository: TrashRepository,
    private val fileSystemDataSource: FileSystemDataSource,
    private val indexRepository: FileIndexRepository,
) {

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }

    /**
     * Recursively copies [items] into the directory at [destinationPath], emitting progress.
     *
     * Directories are recreated at the destination; regular files are streamed with a buffered
     * copy. The source items are left untouched.
     */
    fun copy(items: List<FileItem>, destinationPath: String): Flow<FileOperationProgress> =
        transfer(items, destinationPath, FileOperationType.COPY)

    /**
     * Recursively moves [items] into the directory at [destinationPath], emitting progress.
     *
     * A rename is attempted first (same-volume fast path); otherwise the entry is copied and then
     * the source is deleted once the copy completes successfully.
     */
    fun move(items: List<FileItem>, destinationPath: String): Flow<FileOperationProgress> =
        transfer(items, destinationPath, FileOperationType.MOVE)

    /**
     * "Deletes" [items] by moving each into the app-managed Recycle Bin (see [TrashRepository]).
     *
     * THE CARDINAL RULE — NO DATA LOSS: a file is only ever removed from its original location once
     * it is safely inside the trash. If [TrashRepository.moveToTrash] returns `false` for an item,
     * the source is left untouched (never hard-deleted) and the failure is recorded so the caller
     * can report it. Returns [AppResult.Success] only when every existing item was trashed;
     * otherwise a [AppResult.Failure] so the UI surfaces that some files were preserved.
     */
    suspend fun delete(items: List<FileItem>): AppResult<Unit> = withContext(dispatcher) {
        try {
            var anyFailed = false
            for (item in items) {
                if (!currentCoroutineContext().isActive) {
                    return@withContext AppResult.Failure(
                        AppError.Unknown("Delete cancelled."),
                    )
                }
                val file = File(item.path)
                if (!file.exists()) continue
                // Route through the Recycle Bin. On failure PRESERVE the source (do not fall back
                // to a hard delete) and keep going so one bad item can't abort the rest.
                if (!trashRepository.moveToTrash(item)) {
                    anyFailed = true
                }
            }
            if (anyFailed) {
                AppResult.Failure(
                    AppError.Io("Some items could not be moved to Recycle Bin"),
                )
            } else {
                AppResult.Success(Unit)
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SecurityException) {
            AppResult.Failure(AppError.AccessDenied(e.message ?: "delete"))
        } catch (e: IOException) {
            AppResult.Failure(AppError.Io(e.message ?: "IO error during delete", e))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Unknown error during delete", e))
        }
    }

    /**
     * Shared producer for copy and move. Plans the full set of leaf files (for accurate byte/item
     * totals), then transfers them one by one while emitting progress snapshots.
     */
    private fun transfer(
        items: List<FileItem>,
        destinationPath: String,
        type: FileOperationType,
    ): Flow<FileOperationProgress> = flow {
        val destinationDir = File(destinationPath)

        // Validate destination up front.
        if (!destinationDir.exists()) {
            if (!destinationDir.mkdirs()) {
                emit(
                    failed(type, AppError.Io("Destination does not exist: " + destinationPath).displayMessage),
                )
                return@flow
            }
        } else if (!destinationDir.isDirectory) {
            emit(failed(type, "Destination is not a directory: " + destinationPath))
            return@flow
        }

        // Build a flat plan of every regular file that will be copied, with its destination path.
        val plan = ArrayList<PlannedFile>()
        // Top-level (source item -> destination file) pairs, used to update the file index once the
        // transfer completes successfully. Only the created roots are indexed here; deep re-indexing
        // of copied/moved trees is left to the live observer / next scan per the index contract.
        val topLevelTargets = ArrayList<Pair<FileItem, File>>()
        var totalBytes = 0L
        try {
            for (item in items) {
                if (!currentCoroutineContext().isActive) {
                    emit(cancelled(type))
                    return@flow
                }
                val source = File(item.path)
                if (!source.exists()) {
                    emit(failed(type, AppError.NotFound(item.path).displayMessage))
                    return@flow
                }

                // Guard against copying/moving an entry into its own location. Opening the same
                // canonical path for read and write truncates the source to 0 bytes (and a MOVE
                // would then delete it), so we must never plan such a transfer.
                val sourceCanonical = source.canonicalFile
                var target = File(destinationDir, source.name)
                if (sourceCanonical == target.canonicalFile) {
                    if (type == FileOperationType.COPY && !source.isDirectory) {
                        // Same-directory copy: auto-rename so source and target differ.
                        target = uniqueCopyTarget(destinationDir, source.name)
                    } else {
                        // Same-location move, or same-directory directory copy: reject cleanly.
                        emit(
                            failed(
                                type,
                                "Cannot ${type.name.lowercase()} \"${source.name}\" into its own location.",
                            ),
                        )
                        return@flow
                    }
                } else if (source.isDirectory && isInside(sourceCanonical, target.canonicalFile)) {
                    // Moving/copying a directory into itself (or a sub-path) would recurse forever
                    // and destroy data; reject it.
                    emit(
                        failed(
                            type,
                            "Cannot ${type.name.lowercase()} folder \"${source.name}\" into itself.",
                        ),
                    )
                    return@flow
                }
                totalBytes += planEntry(source, target, plan)
                topLevelTargets.add(item to target)
            }

            // A transfer must never silently overwrite a file or folder that was already
            // present at its destination. Validate the ENTIRE plan before the first output is
            // created so a later collision cannot leave an earlier selected item copied/moved
            // while the operation reports failure. This also rejects two selected roots that
            // would resolve to the same target name.
            validateNoTargetConflicts(plan)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: DestinationConflictException) {
            emit(failed(type, e.message ?: "A destination item already exists."))
            return@flow
        } catch (e: SecurityException) {
            emit(failed(type, AppError.AccessDenied(e.message ?: destinationPath).displayMessage))
            return@flow
        } catch (e: Exception) {
            emit(failed(type, e.message ?: "Failed to enumerate source files."))
            return@flow
        }

        val totalItems = plan.size
        var processedItems = 0
        var processedBytes = 0L

        // Initial running snapshot so observers see the operation start immediately.
        emit(
            FileOperationProgress(
                type = type,
                state = OperationState.RUNNING,
                processedItems = 0,
                totalItems = totalItems,
                processedBytes = 0L,
                totalBytes = totalBytes,
                currentFileName = "",
            ),
        )

        // Track top-level move sources so they can be pruned after a cross-volume copy.
        val moveSources: List<File> =
            if (type == FileOperationType.MOVE) items.map { File(it.path) } else emptyList()

        try {
            for (planned in plan) {
                if (!currentCoroutineContext().isActive) {
                    emit(
                        cancelled(
                            type = type,
                            processedItems = processedItems,
                            totalItems = totalItems,
                            processedBytes = processedBytes,
                            totalBytes = totalBytes,
                        ),
                    )
                    return@flow
                }

                if (planned.isDirectory) {
                    createDirectoryNoOverwrite(planned.target)
                    continue
                }

                // Ensure parent directory exists for the file.
                planned.target.parentFile?.let { parent ->
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw IOException("Failed to create directory: " + parent.absolutePath)
                    }
                    if (!parent.isDirectory) {
                        throw DestinationConflictException(
                            "Destination already exists: " + parent.absolutePath,
                        )
                    }
                }

                processedBytes = copyFileStreaming(
                    type = type,
                    source = planned.source,
                    target = planned.target,
                    baseProcessedBytes = processedBytes,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    totalBytes = totalBytes,
                )

                processedItems++
                emit(
                    FileOperationProgress(
                        type = type,
                        state = OperationState.RUNNING,
                        processedItems = processedItems,
                        totalItems = totalItems,
                        processedBytes = processedBytes,
                        totalBytes = totalBytes,
                        currentFileName = planned.source.name,
                    ),
                )
            }

            // For a move, delete the original sources now that everything copied successfully.
            if (type == FileOperationType.MOVE) {
                for (src in moveSources) {
                    if (src.exists()) deleteRecursively(src)
                }
            }

            // BEST-EFFORT live index update. Wrapped so an index failure can never change the
            // outcome or safety of the copy/move itself. A COPY indexes each created root; a MOVE
            // records the rename (old path removed, new path indexed) for each moved root.
            runCatching {
                for ((item, target) in topLevelTargets) {
                    if (!target.exists()) continue
                    val newItem = fileSystemDataSource.toFileItem(target)
                    if (type == FileOperationType.MOVE) {
                        indexRepository.onMovedOrRenamed(item.path, newItem)
                    } else {
                        indexRepository.indexFile(newItem)
                    }
                }
            }

            emit(
                FileOperationProgress(
                    type = type,
                    state = OperationState.COMPLETED,
                    processedItems = processedItems,
                    totalItems = totalItems,
                    processedBytes = if (totalBytes > 0) totalBytes else processedBytes,
                    totalBytes = totalBytes,
                    currentFileName = "",
                ),
            )
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: DestinationConflictException) {
            emit(failed(type, e.message ?: "A destination item already exists."))
        } catch (e: SecurityException) {
            emit(failed(type, AppError.AccessDenied(e.message ?: destinationPath).displayMessage))
        } catch (e: IOException) {
            emit(failed(type, e.message ?: "IO error during operation."))
        } catch (e: Exception) {
            emit(failed(type, e.message ?: "Unknown error during operation."))
        }
    }.flowOn(dispatcher)

    /**
     * Streams a single file from [source] to [target] with a buffered copy, emitting incremental
     * byte progress and checking for cancellation between buffers.
     *
     * Returns the new cumulative processed-bytes count ([baseProcessedBytes] plus the bytes copied).
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<FileOperationProgress>.copyFileStreaming(
        type: FileOperationType,
        source: File,
        target: File,
        baseProcessedBytes: Long,
        processedItems: Int,
        totalItems: Int,
        totalBytes: Long,
    ): Long {
        var cumulative = baseProcessedBytes

        // Final safety net: never open the same canonical path for read and write, which would
        // truncate the source to 0 bytes. The planner already guards this, but a defensive check
        // here prevents data loss should a same-path entry ever reach this far.
        if (source.canonicalFile == target.canonicalFile) {
            throw DestinationConflictException(
                "Cannot ${type.name.lowercase()} \"${source.name}\" onto itself.",
            )
        }

        // CREATE_NEW opens the destination atomically only when no entry already exists.
        // This is intentionally a second line of defense after the whole-plan preflight:
        // another process may create a conflicting file between planning and copying. Unlike
        // File.outputStream(), it can never truncate that pre-existing file.
        var createdTarget = false
        try {
            source.inputStream().buffered(BUFFER_SIZE).use { input ->
                val rawOutput = try {
                    Files.newOutputStream(
                        target.toPath(),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                    )
                } catch (collision: FileAlreadyExistsException) {
                    throw DestinationConflictException(
                        "Destination already exists: " + target.absolutePath,
                        collision,
                    )
                }
                createdTarget = true
                rawOutput.buffered(BUFFER_SIZE).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (!currentCoroutineContext().isActive) {
                            // Surrender cooperatively; the partial target is cleaned up below.
                            throw CancellationException("Operation cancelled.")
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        cumulative += read
                        emit(
                            FileOperationProgress(
                                type = type,
                                state = OperationState.RUNNING,
                                processedItems = processedItems,
                                totalItems = totalItems,
                                processedBytes = cumulative,
                                totalBytes = totalBytes,
                                currentFileName = source.name,
                            ),
                        )
                    }
                    output.flush()
                }
            }
        } catch (error: Throwable) {
            // We only ever remove a target this invocation created. A conflicting pre-existing
            // target must remain byte-for-byte untouched even when the transfer is cancelled or
            // fails after the collision is noticed.
            if (createdTarget) runCatching { target.delete() }
            throw error
        }
        // Preserve last-modified timestamp where possible.
        runCatching { target.setLastModified(source.lastModified()) }
        return cumulative
    }

    /**
     * Returns a destination [File] in [dir] whose name does not collide with an existing entry,
     * derived from [name] by inserting " (copy)" (and " (copy N)" for further collisions) before
     * the extension. Used for same-directory COPY so source and target never share a path.
     */
    private fun uniqueCopyTarget(dir: File, name: String): File {
        val dotIndex = name.lastIndexOf('.')
        val hasExtension = dotIndex > 0
        val base = if (hasExtension) name.substring(0, dotIndex) else name
        val extension = if (hasExtension) name.substring(dotIndex) else ""

        var candidate = File(dir, "$base (copy)$extension")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(dir, "$base (copy $counter)$extension")
            counter++
        }
        return candidate
    }

    /** True when [child] is the same as, or nested within, [parent] (both canonical paths). */
    private fun isInside(parent: File, child: File): Boolean {
        var current: File? = child
        while (current != null) {
            if (current == parent) return true
            current = current.parentFile
        }
        return false
    }

    /**
     * Recursively expands [source] into the [plan], appending one [PlannedFile] per directory and
     * regular file (in pre-order so directories are created before their children). Returns the
     * total byte count of regular files discovered.
     */
    private fun planEntry(source: File, target: File, plan: MutableList<PlannedFile>): Long {
        if (source.isDirectory) {
            plan.add(PlannedFile(source, target, isDirectory = true))
            var bytes = 0L
            val children = source.listFiles() ?: emptyArray()
            for (child in children) {
                bytes += planEntry(child, File(target, child.name), plan)
            }
            return bytes
        }
        plan.add(PlannedFile(source, target, isDirectory = false))
        return source.length()
    }

    /**
     * Rejects any plan whose output would overwrite a pre-existing filesystem entry or another
     * output in the same operation. This runs after every source has been expanded but before the
     * first write, preserving the all-or-nothing *preflight* guarantee for collision failures.
     */
    private fun validateNoTargetConflicts(plan: List<PlannedFile>) {
        val plannedPaths = HashSet<String>(plan.size)
        for (planned in plan) {
            val canonicalTarget = planned.target.canonicalFile
            if (canonicalTarget.exists()) {
                throw DestinationConflictException(
                    "Destination already exists: " + canonicalTarget.absolutePath,
                )
            }
            if (!plannedPaths.add(canonicalTarget.path)) {
                throw DestinationConflictException(
                    "Multiple selected items would use destination: " + canonicalTarget.absolutePath,
                )
            }
        }
    }

    /** Creates one planned directory without merging into or replacing an existing entry. */
    private fun createDirectoryNoOverwrite(target: File) {
        if (target.exists()) {
            throw DestinationConflictException("Destination already exists: " + target.absolutePath)
        }
        if (!target.mkdir()) {
            if (target.exists()) {
                throw DestinationConflictException("Destination already exists: " + target.absolutePath)
            }
            throw IOException("Failed to create directory: " + target.absolutePath)
        }
    }

    /** Deletes [file] and, if it is a directory, all of its contents. Returns true on full success. */
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) {
            val children = file.listFiles() ?: emptyArray()
            for (child in children) {
                if (!deleteRecursively(child)) return false
            }
        }
        return file.delete() || !file.exists()
    }

    private fun failed(type: FileOperationType, message: String): FileOperationProgress =
        FileOperationProgress(
            type = type,
            state = OperationState.FAILED,
            errorMessage = message,
        )

    private fun cancelled(
        type: FileOperationType,
        processedItems: Int = 0,
        totalItems: Int = 0,
        processedBytes: Long = 0L,
        totalBytes: Long = 0L,
    ): FileOperationProgress =
        FileOperationProgress(
            type = type,
            state = OperationState.CANCELLED,
            processedItems = processedItems,
            totalItems = totalItems,
            processedBytes = processedBytes,
            totalBytes = totalBytes,
        )

    /** A single planned leaf in a copy/move operation: where it comes from and where it goes. */
    private data class PlannedFile(
        val source: File,
        val target: File,
        val isDirectory: Boolean,
    )

    /** Distinguishes an intentional no-overwrite rejection from an ordinary IO failure. */
    private class DestinationConflictException(
        message: String,
        cause: Throwable? = null,
    ) : IOException(message, cause)
}
