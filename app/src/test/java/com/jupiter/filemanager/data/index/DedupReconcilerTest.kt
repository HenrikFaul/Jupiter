package com.jupiter.filemanager.data.index

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.data.permission.StorageAccessManager
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric end-to-end proof of the ACTUAL fix: a file that "arrived while the app was dead" is
 * caught by the checkpoint reconciler and flagged as a duplicate — the exact scenario the
 * real-time observer alone could never handle.
 *
 * Uses the REAL [DuplicateDetector] over an in-memory Room index and REAL files on disk, the REAL
 * [StorageAccessManager] (with legacy storage permissions granted via Robolectric), a scripted
 * [NewFileSource], and an in-memory checkpoint store. The detection logic is not mocked.
 *
 * Runs under SDK 28 so `hasAllFilesAccess()` resolves via the legacy READ/WRITE grant (granted
 * below) — a stable Robolectric path that needs no All-Files-Access shadow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DedupReconcilerTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var detector: DuplicateDetector
    private lateinit var storageAccess: StorageAccessManager
    private lateinit var tempDir: File

    /** In-memory, controllable checkpoint store. */
    private class FakeCheckpointStore(var enabled: Boolean = true, var checkpoint: Long = 0L) :
        DedupCheckpointStore {
        override suspend fun isIndexingEnabled() = enabled
        override suspend fun getCheckpointId() = checkpoint
        override suspend fun setCheckpointId(value: Long) {
            if (value > checkpoint) checkpoint = value
        }
    }

    /** Scripted delta source keyed on `_id`. */
    private class FakeNewFileSource(
        val maxId: Long,
        val newFiles: List<MediaStoreIndexSource.NewFile> = emptyList(),
    ) : NewFileSource {
        override suspend fun maxObservedId() = maxId
        override suspend fun queryNewSince(sinceId: Long, limit: Int) =
            newFiles.filter { it.id > sinceId }.sortedBy { it.id }.take(limit)
    }

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Shadows.shadowOf(ctx as Application).grantPermissions(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        detector = DuplicateDetector(ctx, repo, PerceptualHashSource(), dispatcher)
        storageAccess = StorageAccessManager(ctx)
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-reconcile").toFile()
    }

    @After
    fun tearDown() {
        db.close()
        tempDir.deleteRecursively()
    }

    private fun item(file: File) = FileItem(
        path = file.absolutePath,
        name = file.name,
        isDirectory = false,
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
        type = FileType.OTHER,
        extension = file.extension,
    )

    @Test
    fun fileArrivingWhileAppWasClosedIsCaughtByReconciler() = runTest(dispatcher) {
        // An original already on the device, indexed by a past survey (METADATA ONLY, no hash).
        val payload = ByteArray(9000) { (it % 251).toByte() }
        val original = File(tempDir, "photo.jpg").apply { writeBytes(payload) }
        repo.indexFile(item(original))

        // A byte-identical copy that landed while the app was dead (e.g. re-downloaded).
        val copy = File(tempDir, "photo (1).jpg").apply { writeBytes(payload) }

        val store = FakeCheckpointStore(enabled = true, checkpoint = 0L)
        val source = FakeNewFileSource(
            maxId = 5_000L,
            newFiles = listOf(MediaStoreIndexSource.NewFile(item(copy), id = 5_001L)),
        )
        val reconciler = DedupReconciler(source, detector, store, storageAccess, dispatcher)

        val alerts = mutableListOf<DuplicateAlert>()
        val collectJob = launch { detector.observeDuplicateAlerts().collect { alerts.add(it) } }

        // First run establishes the baseline WITHOUT alerting on the existing library.
        val first = reconciler.reconcile()
        assertEquals("baseline run inspects nothing", 0, first)
        assertEquals("checkpoint set to the current max id", 5_000L, store.checkpoint)
        assertTrue("no alerts on baseline", alerts.isEmpty())

        // Second run: the copy has a higher id than the checkpoint → inspected and flagged.
        val second = reconciler.reconcile()
        assertEquals("the newly-arrived copy is inspected", 1, second)
        assertEquals("checkpoint advanced past the copy", 5_001L, store.checkpoint)

        assertEquals("exactly one duplicate alert fired", 1, alerts.size)
        val alert = alerts.single()
        assertEquals(DuplicateKind.EXACT, alert.kind)
        assertEquals(copy.absolutePath, alert.newFile.path)
        assertTrue(
            "the alert points at the pre-existing original",
            alert.existing.any { it.path == original.absolutePath },
        )

        // Third run: nothing newer than the checkpoint → no work, no re-alert.
        val third = reconciler.reconcile()
        assertEquals(0, third)
        assertEquals("no duplicate re-alert for the same file", 1, alerts.size)

        collectJob.cancel()
    }

    @Test
    fun reconcilerDoesNothingWhenIndexingDisabled() = runTest(dispatcher) {
        val store = FakeCheckpointStore(enabled = false, checkpoint = 0L)
        val source = FakeNewFileSource(maxId = 5_000L)
        val reconciler = DedupReconciler(source, detector, store, storageAccess, dispatcher)

        assertEquals(0, reconciler.reconcile())
        assertEquals("checkpoint untouched when disabled", 0L, store.checkpoint)
    }

    /**
     * The baseline-poisoning fix: when MediaStore reports no id (empty, or not yet readable), the
     * checkpoint must STAY 0 — never be pinned to a low value — so the whole library is not
     * later re-alerted as "new".
     */
    @Test
    fun emptyOrUnreadableMediaStoreDoesNotPoisonTheBaseline() = runTest(dispatcher) {
        val store = FakeCheckpointStore(enabled = true, checkpoint = 0L)
        val source = FakeNewFileSource(maxId = 0L) // 0 = empty / unreadable
        val reconciler = DedupReconciler(source, detector, store, storageAccess, dispatcher)

        assertEquals(0, reconciler.reconcile())
        assertEquals("no bogus baseline pinned", 0L, store.checkpoint)
    }
}
