package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.domain.model.FileOperationProgress
import com.jupiter.filemanager.domain.model.OperationState
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Enforces the Zip-Slip (path-traversal) safety contract of [ArchiveManager.extractZip].
 *
 * Malicious archive entries whose resolved canonical path escapes the extraction root
 * must (a) never materialize a file outside that root and (b) terminate the extraction
 * flow in [OperationState.FAILED]. A benign control archive must extract to
 * [OperationState.COMPLETED] with its files present.
 *
 * All assertions mirror the real behavior read from ArchiveManager.kt:
 *  - `resolveSafeEntry` throws IOException for any entry whose canonical path leaves the
 *    root ("../" style / traversal), which the extract flow catches and turns into a
 *    terminal FAILED snapshot (the partial output dir may remain, but the escape target
 *    outside the root is never created).
 *  - A leading-slash / absolute-style entry is re-based INSIDE the root by
 *    `File(targetRoot, entryName)` on Unix (the leading '/' is stripped), so it does not
 *    escape; we assert only that no file appears at the absolute location outside root.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveZipSlipTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun manager(): ArchiveManager = ArchiveManager(UnconfinedTestDispatcher())

    /** Writes a zip at [zipFile] containing the given file entries (name -> bytes). */
    private fun writeZip(zipFile: File, entries: List<Pair<String, ByteArray>>) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    private suspend fun extractAll(
        zipFile: File,
        targetDir: File,
    ): List<FileOperationProgress> =
        manager().extractZip(zipFile.absolutePath, targetDir.absolutePath).toList()

    @Test
    fun extractZip_rejectsTraversalEntries_endsFailed_andNeverEscapesRoot() = runTest {
        // Traversal payloads that resolve OUTSIDE the extraction root.
        val traversalNames = listOf(
            "../escape.txt",
            "../../escape.txt",
            "foo/../../escape.txt",
        )

        for ((index, entryName) in traversalNames.withIndex()) {
            // A dedicated sandbox per case: `outside/` is the parent, `outside/target`
            // is the root we extract into, so "../escape.txt" would land in `outside/`.
            val outside = temp.newFolder("case_$index")
            val target = File(outside, "target")

            val zipFile = File(outside, "malicious.zip")
            writeZip(zipFile, listOf(entryName to "pwned".toByteArray()))

            val emissions = extractAll(zipFile, target)

            // (b) The flow terminates in FAILED.
            val last = emissions.last()
            assertEquals(
                "traversal entry '$entryName' must end FAILED",
                OperationState.FAILED,
                last.state,
            )
            assertNotNull(
                "FAILED emission for '$entryName' must carry an error message",
                last.errorMessage,
            )

            // (a) No file was created outside the extraction root.
            val escape = File(outside, "escape.txt")
            assertFalse(
                "escape file must NOT exist outside root for '$entryName'",
                escape.exists(),
            )
            // Nothing leaked one level further up either.
            val escapeUp = File(outside.parentFile, "escape.txt")
            assertFalse(
                "escape file must NOT exist above root for '$entryName'",
                escapeUp.exists(),
            )
        }
    }

    @Test
    fun extractZip_absoluteStyleEntry_isConfinedToRoot_neverWritesOutside() = runTest {
        val outside = temp.newFolder("abs_case")
        val target = File(outside, "target")

        // A separate, controlled absolute location the malicious entry tries to hit.
        val forbiddenDir = temp.newFolder("forbidden")
        val forbidden = File(forbiddenDir, "abs_escape.txt")
        assertFalse("precondition: forbidden target absent", forbidden.exists())

        // Leading-slash absolute-style entry name pointing at the forbidden path.
        val absEntry = forbidden.absolutePath // e.g. "/.../forbidden/abs_escape.txt"

        val zipFile = File(outside, "abs.zip")
        writeZip(zipFile, listOf(absEntry to "pwned".toByteArray()))

        extractAll(zipFile, target)

        // The absolute location outside the root must remain untouched. (File(root, "/x")
        // re-bases the entry inside the root on Unix, so no write escapes.)
        assertFalse(
            "absolute-style entry must not write outside the extraction root",
            forbidden.exists(),
        )
    }

    @Test
    fun extractZip_benignArchive_extractsToCompleted_withFilesPresent() = runTest {
        val outside = temp.newFolder("benign")
        val target = File(outside, "target")

        val topBytes = "top-level content".toByteArray()
        val nestedBytes = "nested content".toByteArray()

        val zipFile = File(outside, "benign.zip")
        writeZip(
            zipFile,
            listOf(
                "hello.txt" to topBytes,
                "sub/nested.txt" to nestedBytes,
            ),
        )

        val emissions = extractAll(zipFile, target)

        val last = emissions.last()
        assertEquals(
            "benign archive must end COMPLETED",
            OperationState.COMPLETED,
            last.state,
        )

        val hello = File(target, "hello.txt")
        val nested = File(target, "sub/nested.txt")
        assertTrue("hello.txt must exist", hello.isFile)
        assertTrue("sub/nested.txt must exist", nested.isFile)
        assertTrue("hello.txt bytes must match", hello.readBytes().contentEquals(topBytes))
        assertTrue("nested.txt bytes must match", nested.readBytes().contentEquals(nestedBytes))
    }
}
