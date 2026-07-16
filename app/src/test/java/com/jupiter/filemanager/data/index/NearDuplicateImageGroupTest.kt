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
 * Real proof that VISUAL near-duplicate IMAGE grouping now reaches the cleanup pipeline
 * ([FileIndexRepositoryImpl.nearDuplicateImageGroups]) — the perceptual dHash detector that used to
 * only fire arrival notifications. Byte-different photos with the SAME or a CLOSE dHash are clustered
 * (so re-sized/re-encoded copies of one photo group together), far-apart hashes stay separate, and
 * singletons are dropped. Uses real temp files (the repo existence-prunes) + real in-memory Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NearDuplicateImageGroupTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-near-img").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    /** Indexes a real image file with the given perceptual [hash] and returns its path. */
    private suspend fun image(
        name: String,
        sizeBytes: Long,
        hash: Long,
        width: Int = 0,
        height: Int = 0,
    ): String {
        val file = File(tempDir, name).apply { writeText("x") }
        val item = FileItem(
            path = file.absolutePath,
            name = name,
            isDirectory = false,
            sizeBytes = sizeBytes,
            lastModified = 0L,
            type = FileType.IMAGE,
            extension = "jpg",
        )
        repo.upsert(listOf(item))
        // Production grouping requires the full stack; using the same scripted value for all
        // layers keeps this test focused on grouping/LSH rather than Android bitmap decoding.
        repo.putPerceptualFingerprint(file.absolutePath, hash, hash, hash, width, height)
        return file.absolutePath
    }

    @Test
    fun clustersIdenticalAndNearHashesButNotFarOnesOrSingletons() = runTest(dispatcher) {
        // Cluster 1: identical dHash (a,b) + a near one 2 bits off (c) — all one photo re-saved.
        val a = image("a.jpg", sizeBytes = 300, hash = 0x0L)
        val b = image("b.jpg", sizeBytes = 100, hash = 0x0L)      // identical hash
        val c = image("c.jpg", sizeBytes = 200, hash = 0b11L)     // 2 bits off → within threshold 8
        // Cluster 2: a separate identical pair, far (16 bits) from cluster 1.
        val f = image("f.jpg", sizeBytes = 50, hash = 0x000000000000FFFFL)
        val g = image("g.jpg", sizeBytes = 60, hash = 0x000000000000FFFFL)
        // Singletons: hashes far (>8 bits) from every cluster AND each other → must stay ungrouped.
        image("d.jpg", sizeBytes = 999, hash = 0x7FFF000000000000L)  // 15 bits, high
        image("e.jpg", sizeBytes = 999, hash = 0x0000FFFF00000000L)  // 16 bits, bits 32–47

        val groups = repo.nearDuplicateImageGroups(threshold = 8)

        assertEquals("two near-duplicate clusters expected", 2, groups.size)
        assertTrue("groups must be flagged as visual/similar", groups.all { it.similar })

        val byPaths = groups.associateBy { grp -> grp.files.map { it.path }.toSet() }
        assertTrue("a,b,c must cluster together", byPaths.keys.any { it == setOf(a, b, c) })
        assertTrue("f,g must cluster together", byPaths.keys.any { it == setOf(f, g) })

        val bigCluster = groups.first { it.files.size == 3 }
        // Largest copy first, so "keep best"/"select extras" defaults to the highest-res image.
        assertEquals(a, bigCluster.files.first().path)
        assertEquals(listOf(300L, 200L, 100L), bigCluster.files.map { it.sizeBytes })
    }

    @Test
    fun lshBandingCatchesNearHashesWhoseDifferingBitsSpanDifferentBytes() = runTest(dispatcher) {
        // 3 differing bits placed in THREE different bytes (0, 3, 6). A naive 8-band pigeonhole still
        // leaves 5 bands identical, so LSH must surface the pair; total Hamming = 3 ≤ threshold.
        val base = image("base.jpg", sizeBytes = 200, hash = 0x0L)
        val near = image("near.jpg", sizeBytes = 100, hash = 0x0080000001000001L) // bits in byte6, byte3, byte0
        // An unrelated far image that must stay out.
        image("far.jpg", sizeBytes = 100, hash = 0x0F0F0F0F0F0F0F0FL)

        val groups = repo.nearDuplicateImageGroups(threshold = 8)

        assertEquals(1, groups.size)
        assertEquals(setOf(base, near), groups.first().files.map { it.path }.toSet())
    }

    @Test
    fun returnsEmptyWhenNoImageHasANearNeighbour() = runTest(dispatcher) {
        image("only.jpg", sizeBytes = 100, hash = 0x1L)
        image("far.jpg", sizeBytes = 100, hash = 0xFFFF_FFFFL)
        assertEquals(emptyList<Any>(), repo.nearDuplicateImageGroups(threshold = 8))
    }

    @Test
    fun imageBridgeCannotTransitivelyMergeUnrelatedEndpoints() = runTest(dispatcher) {
        val baseHash = 0x1234_5678_9ABC_DEF0L
        val a = image("bridge-a.jpg", 300, baseHash)
        val b = image("bridge-b.jpg", 200, baseHash xor 0x1FL) // 5 bits from A
        val c = image("bridge-c.jpg", 100, baseHash xor 0x3FFL) // 5 from B, 10 from A

        val groups = repo.nearDuplicateImageGroups(threshold = 8)

        assertEquals(1, groups.size)
        assertEquals(setOf(a, b), groups.single().files.map { it.path }.toSet())
        assertTrue(groups.none { group -> group.files.any { it.path == c } })
    }

    @Test
    fun identicalHashesWithIncompatibleAspectRatiosDoNotGroup() = runTest(dispatcher) {
        val hash = 0x1234_5678_9ABC_DEF0L
        image("landscape.jpg", 300, hash, width = 1920, height = 1080)
        image("square.jpg", 200, hash, width = 1080, height = 1080)

        assertTrue(repo.nearDuplicateImageGroups(threshold = 8).isEmpty())
    }
}
