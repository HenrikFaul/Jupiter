package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Repository-level proof for the Similar tab's non-image groups: structural/media fingerprints
 * already in the index are surfaced as `similar = true` duplicate-review groups, not just as
 * one-off arrival notifications.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NearDuplicateStructuralGroupTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-struct-groups").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private suspend fun indexed(name: String, type: FileType, hash: Long): String {
        val file = File(tempDir, name).apply { writeText(name) }
        repo.upsert(
            listOf(
                FileItem(
                    path = file.absolutePath,
                    name = name,
                    isDirectory = false,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified(),
                    type = type,
                    extension = file.extension,
                ),
            ),
        )
        when (type) {
            FileType.VIDEO -> repo.putMediaFingerprint(
                file.absolutePath,
                MediaFingerprint(List(5) { hash }, extent = 10_000L),
            )
            FileType.PDF -> repo.putMediaFingerprint(
                file.absolutePath,
                MediaFingerprint(List(3) { hash }, extent = 3L),
            )
            FileType.AUDIO -> repo.putMediaFingerprint(
                file.absolutePath,
                MediaFingerprint(listOf(hash), extent = 10_000L),
            )
            else -> repo.putStructuralHash(file.absolutePath, hash)
        }
        return file.absolutePath
    }

    @Test
    fun structuralGroupsIncludeTextArchiveAndMediaSimilarity() = runTest(dispatcher) {
        val textA = indexed("a.kt", FileType.CODE, 0x1000L)
        val textB = indexed("b.kt", FileType.CODE, 0x1003L) // Hamming distance 2
        indexed("far.kt", FileType.CODE, 0x7FFF_FFFFL)

        val zipA = indexed("a.zip", FileType.ARCHIVE, 0x55L)
        val zipB = indexed("b.zip", FileType.ARCHIVE, 0x55L)

        val videoBase = 0x1234_5678_9ABC_DEF0L
        val videoA = indexed("a.mp4", FileType.VIDEO, videoBase)
        val videoB = indexed("b.mp4", FileType.VIDEO, videoBase xor 0b1L)

        val groups = repo.nearDuplicateStructuralGroups()

        assertTrue(groups.all { it.similar })
        val pathSets = groups.map { group -> group.files.map { it.path }.toSet() }
        assertTrue(pathSets.contains(setOf(textA, textB)))
        assertTrue(pathSets.contains(setOf(zipA, zipB)))
        assertTrue(pathSets.contains(setOf(videoA, videoB)))
        assertEquals("three structural/media similar groups expected", 3, groups.size)
    }

    @Test
    fun videoBridgeCannotTransitivelyMergeUnrelatedEndpoints() = runTest(dispatcher) {
        val base = 0x1234_5678_9ABC_DEF0L
        val a = indexed("bridge-a.mp4", FileType.VIDEO, base)
        val b = indexed("bridge-b.mp4", FileType.VIDEO, base xor 0x1FL) // 5 bits from A
        val c = indexed("bridge-c.mp4", FileType.VIDEO, base xor 0x3FFL) // 5 from B, 10 from A

        val videoGroups = repo.nearDuplicateStructuralGroups()
            .filter { group -> group.files.all { it.type == FileType.VIDEO } }

        assertEquals("only the mutually-confirmed pair may remain", 1, videoGroups.size)
        assertEquals(setOf(a, b), videoGroups.single().files.map { it.path }.toSet())
        assertTrue(videoGroups.none { group -> group.files.any { it.path == c } })
    }
}
