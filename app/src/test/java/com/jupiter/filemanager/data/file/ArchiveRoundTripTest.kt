package com.jupiter.filemanager.data.file

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.OperationState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

/**
 * Round-trips a directory tree through [ArchiveManager.createZip] +
 * [ArchiveManager.extractZip], verifying byte-for-byte fidelity AND that an EMPTY
 * subdirectory survives the round trip (the 0.6.0 empty-dir preservation fix, whereby
 * createZip emits an explicit "name/" directory entry and extractZip recreates it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchiveRoundTripTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var manager: ArchiveManager

    @Before
    fun setUp() {
        manager = ArchiveManager(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        // TemporaryFolder deletes its tree automatically after each test; nothing else
        // is created outside it, so no additional cleanup is required.
    }

    /** Builds a directory [FileItem] pointing at a real temp directory. */
    private fun dirItem(dir: File): FileItem = FileItem(
        path = dir.absolutePath,
        name = dir.name,
        isDirectory = true,
        sizeBytes = 0L,
        lastModified = dir.lastModified(),
        type = FileType.FOLDER,
        extension = "",
    )

    @Test
    fun createZip_thenExtractZip_preservesBytesAndEmptyDirectories() = runTest {
        // ---- Arrange: a temp tree ----
        //   payload/
        //     a.txt            (known bytes)
        //     sub/b.txt        (nested file, known bytes)
        //     empty/           (EMPTY directory)
        val payload = temp.newFolder("payload")

        val aBytes = "alpha-content-0123456789".toByteArray()
        val bBytes = "nested-bravo-content".toByteArray()

        val aFile = File(payload, "a.txt").apply { writeBytes(aBytes) }
        val subDir = File(payload, "sub").apply { mkdirs() }
        val bFile = File(subDir, "b.txt").apply { writeBytes(bBytes) }
        val emptyDir = File(payload, "empty").apply { mkdirs() }

        assertTrue("precondition: a.txt exists", aFile.isFile)
        assertTrue("precondition: sub/b.txt exists", bFile.isFile)
        assertTrue("precondition: empty/ exists", emptyDir.isDirectory)
        assertEquals("precondition: empty/ is empty", 0, emptyDir.list()?.size ?: -1)

        val zipFile = File(temp.root, "out.zip")

        // ---- Act: compress ----
        val createEmissions =
            manager.createZip(listOf(dirItem(payload)), zipFile.absolutePath).toList()

        // ---- Assert: compression completed and produced a non-empty archive ----
        assertEquals(
            "createZip must end COMPLETED",
            OperationState.COMPLETED,
            createEmissions.last().state,
        )
        assertTrue("zip file must exist", zipFile.isFile)
        assertTrue("zip file must be non-empty", zipFile.length() > 0L)

        // ---- Act: extract into a fresh directory ----
        val extractDir = temp.newFolder("extracted")
        val extractEmissions =
            manager.extractZip(zipFile.absolutePath, extractDir.absolutePath).toList()

        // ---- Assert: extraction completed ----
        assertEquals(
            "extractZip must end COMPLETED",
            OperationState.COMPLETED,
            extractEmissions.last().state,
        )

        // Entry names are relative to payload's PARENT, so the top-level "payload"
        // directory is preserved inside the archive.
        val outA = File(extractDir, "payload/a.txt")
        val outB = File(extractDir, "payload/sub/b.txt")
        val outEmpty = File(extractDir, "payload/empty")

        assertTrue("extracted a.txt must exist", outA.isFile)
        assertTrue("extracted sub/b.txt must exist", outB.isFile)
        assertTrue(
            "extracted a.txt bytes must match original",
            outA.readBytes().contentEquals(aBytes),
        )
        assertTrue(
            "extracted sub/b.txt bytes must match original",
            outB.readBytes().contentEquals(bBytes),
        )

        // The empty directory must be recreated as an actual (empty) directory.
        assertTrue("empty/ must be recreated as a directory", outEmpty.isDirectory)
        assertEquals(
            "recreated empty/ must contain no entries",
            0,
            outEmpty.list()?.size ?: -1,
        )
    }
}
