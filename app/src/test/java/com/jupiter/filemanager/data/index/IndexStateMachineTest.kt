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
import java.io.File

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

    /**
     * Read-usability across a rescan: a RUNNING rebuild over a previously-completed
     * generation must keep the index USABLE (its rows are intact — the sweep only runs on
     * success), so screens keep serving the full index instead of collapsing to a partial
     * live walk. A first-ever partial scan and a reset index are NOT usable.
     */
    @Test
    fun indexStaysUsableDuringRescanOverPriorCompleteGeneration() = runTest(dispatcher) {
        assertFalse("no state row yet", state.isUsable())

        val g1 = state.beginScan()
        assertFalse("first-ever partial scan is not usable", state.isUsable())
        repo.upsertScanned(listOf(file("/s/a")), g1)
        state.completeScan(g1, 1)
        assertTrue(state.isUsable())

        state.beginScan() // manual rebuild starts: status RUNNING again
        assertFalse("a rescan in progress is NOT complete", state.isMetadataComplete())
        assertTrue("but the prior complete generation keeps it usable", state.isUsable())

        state.reset()
        assertFalse("a cleared index is not usable", state.isUsable())
    }

    /**
     * The "you already have this" alert precondition, end to end with REAL files: the
     * pre-existing copy was indexed by the survey with METADATA ONLY (contentHash = null,
     * the realistic state — surveys never hash). Detecting the newly-arrived duplicate must
     * hash the same-size candidate on demand instead of silently missing it (defect #5).
     */
    @Test
    fun newFileDuplicateIsFoundEvenWhenOriginalWasNeverHashed() = runTest(dispatcher) {
        val dir = java.nio.file.Files.createTempDirectory("jupiter-dup").toFile()
        try {
            val payload = ByteArray(4096) { (it % 251).toByte() }
            val original = java.io.File(dir, "original.jpg").apply { writeBytes(payload) }
            val copy = java.io.File(dir, "downloaded-again.jpg").apply { writeBytes(payload) }
            // Same size as the duplicates but different content — must NOT be reported.
            val decoy = java.io.File(dir, "decoy.jpg")
                .apply { writeBytes(ByteArray(4096) { (it % 7).toByte() }) }

            fun itemFor(f: java.io.File) = FileItem(
                path = f.absolutePath,
                name = f.name,
                isDirectory = false,
                sizeBytes = f.length(),
                lastModified = f.lastModified(),
                type = FileType.IMAGE,
                extension = "jpg",
            )

            // Survey pass: metadata only, no hashes anywhere.
            val g = state.beginScan()
            repo.upsertScanned(listOf(itemFor(original), itemFor(decoy)), g)
            state.completeScan(g, 2)
            assertNull(db.fileIndexDao().getByPath(original.absolutePath)!!.contentHash)

            val duplicates = repo.findContentDuplicates(itemFor(copy))

            assertEquals(listOf(original.absolutePath), duplicates.map { it.path })

            // Size floor: empty files are byte-identical by construction (pending downloads,
            // .nomedia markers) — they must NEVER alert, whatever matches exist.
            val emptyIndexed = java.io.File(dir, "a.nomedia").apply { writeBytes(ByteArray(0)) }
            val emptyArriving = java.io.File(dir, "b.nomedia").apply { writeBytes(ByteArray(0)) }
            repo.upsert(listOf(itemFor(emptyIndexed)))
            assertTrue(repo.findContentDuplicates(itemFor(emptyArriving)).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * The perceptual fingerprint must survive a rescan of an UNCHANGED image (else every
     * 12 h survey would wipe all fingerprints and force the backfill to re-decode the whole
     * photo library), and must be cleared when the file's identity changed (new content →
     * stale fingerprint would cause false "similar" alerts).
     */
    @Test
    fun rescanPreservesPerceptualHashForUnchangedImageAndClearsItOnChange() = runTest(dispatcher) {
        val img = file("/s/pic.jpg", size = 5_000L, mtime = 7L)
        val g1 = state.beginScan()
        repo.upsertScanned(listOf(img), g1)
        state.completeScan(g1, 1)
        repo.putPerceptualHash("/s/pic.jpg", 0x1234L)

        // Rescan with identical identity → fingerprint kept (and re-stamped generation).
        val g2 = state.beginScan()
        repo.upsertScanned(listOf(img), g2)
        state.completeScan(g2, 1)
        assertEquals(0x1234L, db.fileIndexDao().getByPath("/s/pic.jpg")!!.perceptualHash)

        // Rescan with CHANGED identity (edited file) → fingerprint cleared for recompute.
        val g3 = state.beginScan()
        repo.upsertScanned(listOf(file("/s/pic.jpg", size = 6_000L, mtime = 9L)), g3)
        state.completeScan(g3, 1)
        assertNull(db.fileIndexDao().getByPath("/s/pic.jpg")!!.perceptualHash)
    }

    /**
     * Near-duplicate lookup: returns images within the Hamming threshold, excludes the
     * queried path itself, far hashes, and UNHASHABLE-marked rows.
     */
    @Test
    fun findNearDuplicateImagesMatchesWithinThresholdOnly() = runTest(dispatcher) {
        // Real on-disk files: findNearDuplicateImages now prunes results whose file has
        // vanished (or sits in a trash dir), so a duplicate alert never points at a
        // deleted/trashed file — the comparison set must physically exist.
        val dir = java.nio.file.Files.createTempDirectory("jupiter-near").toFile()
        try {
            val a = File(dir, "a.jpg").apply { writeText("a") }
            val b = File(dir, "b.png").apply { writeText("b") }
            val c = File(dir, "c.jpg").apply { writeText("c") }
            val broken = File(dir, "broken.jpg").apply { writeText("x") }
            repo.upsert(
                listOf(
                    file(a.absolutePath, size = 5_000L),
                    file(b.absolutePath, size = 6_000L),
                    file(c.absolutePath, size = 7_000L),
                    file(broken.absolutePath, size = 8_000L),
                ),
            )
            val base = 0b1111L
            repo.putPerceptualFingerprint(a.absolutePath, base, base, base)
            repo.putPerceptualFingerprint(
                b.absolutePath,
                base xor 0b11L,
                base xor 0b11L,
                base xor 0b11L,
            ) // distance 2 in every family → near
            repo.putPerceptualFingerprint(c.absolutePath, base.inv(), base.inv(), base.inv())
            repo.putPerceptualFingerprint(
                broken.absolutePath,
                PerceptualHash.UNHASHABLE,
                PerceptualHash.UNHASHABLE,
                PerceptualHash.UNHASHABLE,
            )

            val near = repo.findNearDuplicateImages(
                path = a.absolutePath,
                fingerprint = PerceptualFingerprint(base, base, base),
                threshold = PerceptualHash.DEFAULT_NEAR_THRESHOLD,
            )
            assertEquals(listOf(b.absolutePath), near.map { it.path })
        } finally {
            dir.deleteRecursively()
        }
    }

    /**
     * Hash back-fill must not disturb the survey's generation stamp: a whole-row upsert from
     * a pre-hash snapshot would revert `lastSeenGeneration` and get a LIVE file's row swept
     * as stale. The targeted [FileIndexDao.updateHash] leaves the stamp intact.
     */
    @Test
    fun hashBackfillPreservesGenerationStamp() = runTest(dispatcher) {
        val g = state.beginScan()
        repo.upsertScanned(listOf(file("/s/x", size = 9000L, mtime = 3L)), g)

        db.fileIndexDao().updateHash(
            path = "/s/x",
            sizeBytes = 9000L,
            lastModified = 3L,
            legacyHash = "HASH",
            digest = null,
            indexedAt = 42L,
        )

        val row = db.fileIndexDao().getByPath("/s/x")!!
        assertEquals("HASH", row.contentHash)
        assertEquals("the survey's generation stamp must survive", g, row.lastSeenGeneration)

        repo.sweepStaleGenerations(g)
        assertNotNull("the row must not be swept", db.fileIndexDao().getByPath("/s/x"))
    }
}
