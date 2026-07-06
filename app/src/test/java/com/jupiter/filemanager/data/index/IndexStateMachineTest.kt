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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric-backed Room tests for the index life-cycle. These run under the JVM
 * `testDebugUnitTest` task (no emulator), so the generation/state-machine/stale-sweep
 * behavior the architecture review demanded is actually EXERCISED and PROVEN in CI —
 * not merely compiled.
 *
 * A plain [Application] is used (not the @HiltAndroidApp JupiterApp) so Robolectric does
 * not run app startup; the repositories are constructed directly against an in-memory DB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IndexStateMachineTest {

    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var state: IndexStateRepositoryImpl
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        state = IndexStateRepositoryImpl(db.indexStateDao(), dispatcher)
    }

    @After
    fun tearDown() = db.close()

    private fun file(
        path: String,
        size: Long = 10L,
        mtime: Long = 1L,
        dir: Boolean = false,
    ) = FileItem(
        path = path,
        name = path.trimEnd('/').substringAfterLast('/'),
        isDirectory = dir,
        sizeBytes = if (dir) 0L else size,
        lastModified = mtime,
        type = FileType.OTHER,
        extension = "",
    )

    /** #1/#2: a scan that wrote rows but never completed is NOT authoritative. */
    @Test
    fun partialScanIsNeverComplete() = runTest(dispatcher) {
        val gen = state.beginScan()
        repo.upsertScanned(listOf(file("/s/a"), file("/s/b")), gen)
        // No completeScan(): the worker was "killed" mid-build.
        assertFalse("a partial scan must not be complete", state.isMetadataComplete())
        assertEquals(IndexStatus.RUNNING, state.current()!!.status)
    }

    /** #2/#3: a completed scan is authoritative and sweeps globally-stale rows. */
    @Test
    fun completedScanIsCompleteAndSweepsDeletedFiles() = runTest(dispatcher) {
        val g1 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/a"), file("/s/b"), file("/s/c")), g1)
        repo.sweepStaleGenerations(g1)
        state.completeScan(g1, 3)
        assertTrue(state.isMetadataComplete())
        assertEquals(3, repo.allFiles().size)

        // Second survey: /s/c no longer exists on disk (not written this generation).
        val g2 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/a"), file("/s/b")), g2)
        repo.sweepStaleGenerations(g2)
        state.completeScan(g2, 2)

        val paths = repo.allFiles().map { it.path }.toSet()
        assertEquals(setOf("/s/a", "/s/b"), paths) // ghost /s/c swept
        assertEquals(g2, state.current()!!.lastCompleteGeneration)
    }

    /** #3: delta/browse rows (generation 0) are never removed by the survey sweep. */
    @Test
    fun deltaRowsSurviveTheSweep() = runTest(dispatcher) {
        val g1 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/a")), g1)
        repo.sweepStaleGenerations(g1)
        state.completeScan(g1, 1)

        // A newly-downloaded file indexed via a delta (generation 0).
        repo.upsert(listOf(file("/downloads/new.bin")))

        // A later survey that does not re-see the delta file must NOT delete it.
        val g2 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/a")), g2)
        repo.sweepStaleGenerations(g2)
        state.completeScan(g2, 1)

        assertTrue("/downloads/new.bin" in repo.allFiles().map { it.path })
    }

    /** #4 (hash cache): a rescan of unchanged files must not discard cached hashes. */
    @Test
    fun rescanPreservesHashForUnchangedFile() = runTest(dispatcher) {
        val f = file("/s/img.jpg", size = 100L, mtime = 5L)
        val g1 = state.beginScan()
        repo.upsertScanned(listOf(f), g1)
        state.completeScan(g1, 1)
        repo.putHash(f, "HASH123")

        // Rescan with identical identity (size + mtime).
        val g2 = state.beginScan()
        repo.upsertScanned(listOf(f), g2)
        state.completeScan(g2, 1)

        assertEquals("HASH123", db.fileIndexDao().getByPath("/s/img.jpg")!!.contentHash)
    }

    /** #5/#7: a directory rename rewrites the WHOLE subtree, preserving hashes, atomically. */
    @Test
    fun renameRewritesSubtreePreservingHash() = runTest(dispatcher) {
        repo.upsert(listOf(file("/s/dir", dir = true), file("/s/dir/child.dat", size = 50L, mtime = 7L)))
        repo.putHash(file("/s/dir/child.dat", size = 50L, mtime = 7L), "CHILDHASH")

        repo.onMovedOrRenamed("/s/dir", file("/s/renamed", dir = true))

        assertNull(db.fileIndexDao().getByPath("/s/dir"))
        assertNull(db.fileIndexDao().getByPath("/s/dir/child.dat"))
        assertNotNull(db.fileIndexDao().getByPath("/s/renamed"))
        val moved = db.fileIndexDao().getByPath("/s/renamed/child.dat")
        assertNotNull("the descendant must survive the rename", moved)
        assertEquals("CHILDHASH", moved!!.contentHash) // hash preserved through the move
    }

    /**
     * #4: the two-phase survey (fast MediaStore SEED + filesystem RECONCILIATION) stamps both
     * phases with the SAME generation, so a directory and a non-media file added by the
     * reconciliation walk survive the sweep alongside the seed's media rows — and directories
     * are indexed (MediaStore has none). Rows from a prior generation are still swept.
     */
    @Test
    fun seedPlusReconcileShareGenerationAndSurviveSweep() = runTest(dispatcher) {
        // A leftover row from an OLD survey that no longer exists on disk.
        val gOld = state.beginScan()
        repo.upsertScanned(listOf(file("/s/gone.old")), gOld)
        state.completeScan(gOld, 1)

        val g = state.beginScan()
        // Phase 1 — MediaStore seed (media files only).
        repo.upsertScanned(listOf(file("/s/media.jpg")), g)
        // Phase 2 — reconciliation adds a non-media file AND a directory, same generation.
        repo.upsertScanned(listOf(file("/s/dir", dir = true), file("/s/notes.txt")), g)
        repo.sweepStaleGenerations(g)
        state.completeScan(g, 3)

        val paths = repo.indexedPaths()
        assertTrue("seed media survives", "/s/media.jpg" in paths)
        assertTrue("reconciled non-media survives", "/s/notes.txt" in paths)
        assertTrue("reconciled directory is indexed", "/s/dir" in paths)
        assertFalse("prior-generation ghost is swept", "/s/gone.old" in paths)
    }

    /**
     * #10 (resumability correctness): an interrupted survey's partial rows must NOT be swept
     * by the resumed survey. `pathsAtGeneration` scopes the walk's skip-set to the CURRENT
     * generation, so the resumed run re-stamps the old rows instead of skipping-then-sweeping
     * them.
     */
    @Test
    fun resumedSurveyRestampsPriorProgressInsteadOfSweepingIt() = runTest(dispatcher) {
        // A prior survey wrote some rows, then was killed before completing.
        val g1 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/partial1"), file("/s/partial2")), g1)
        // (no completeScan — the worker was killed)

        val g2 = state.beginScan()
        // At the start of the resumed run nothing is written for this generation yet, so the
        // reconciliation walk would NOT skip the old rows — it re-sees and re-stamps them.
        assertTrue(repo.pathsAtGeneration(g2).isEmpty())
        repo.upsertScanned(listOf(file("/s/partial1"), file("/s/partial2")), g2)
        repo.sweepStaleGenerations(g2)
        state.completeScan(g2, 2)

        val paths = repo.indexedPaths()
        assertTrue("/s/partial1" in paths)
        assertTrue("/s/partial2" in paths)
    }

    /** A sibling that only shares a textual prefix must not be dragged by the rename. */
    @Test
    fun renameLeavesPrefixSiblingUntouched() = runTest(dispatcher) {
        repo.upsert(listOf(file("/s/photos/a.jpg"), file("/s/photos_2024/b.jpg")))
        repo.onMovedOrRenamed("/s/photos", file("/s/album", dir = true))

        assertNotNull(db.fileIndexDao().getByPath("/s/photos_2024/b.jpg"))
        assertNotNull(db.fileIndexDao().getByPath("/s/album/a.jpg"))
    }
}
