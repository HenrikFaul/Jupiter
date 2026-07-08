package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.data.permission.StorageAccessGate
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM tests for [DedupReconciler]'s checkpoint/paging/baseline logic, against recording
 * fakes for every collaborator. No Room, no files, no SharedFlow timing — so the reconciler's
 * contract (catch up files newer than the checkpoint, advance gap-free, never poison the
 * baseline, respect the gates) is proven deterministically. Detection correctness itself lives
 * in [DuplicateDetectorTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DedupReconcilerTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private class FakeCheckpointStore(var enabled: Boolean = true, var checkpoint: Long = 0L) :
        DedupCheckpointStore {
        override suspend fun isIndexingEnabled() = enabled
        override suspend fun getCheckpointId() = checkpoint
        override suspend fun setCheckpointId(value: Long) {
            if (value > checkpoint) checkpoint = value
        }
    }

    private class FakeNewFileSource(
        val maxId: Long,
        val files: List<MediaStoreIndexSource.NewFile> = emptyList(),
    ) : NewFileSource {
        override suspend fun maxObservedId() = maxId
        override suspend fun queryNewSince(sinceId: Long, limit: Int) =
            files.filter { it.id > sinceId }.sortedBy { it.id }.take(limit)
    }

    /** Records every file handed to the detector. */
    private class RecordingInspector : ArrivalInspector {
        val seen = mutableListOf<FileItem>()
        override suspend fun onFileArrived(item: FileItem): DuplicateAlert? {
            seen.add(item)
            return null
        }
    }

    private class FakeGate(val granted: Boolean) : StorageAccessGate {
        override fun hasFullAccess() = granted
    }

    private fun file(id: Long) = MediaStoreIndexSource.NewFile(
        FileItem(
            path = "/storage/emulated/0/Download/file$id.bin",
            name = "file$id.bin",
            isDirectory = false,
            sizeBytes = 100L,
            lastModified = 1L,
            type = FileType.OTHER,
            extension = "bin",
        ),
        id = id,
    )

    @Test
    fun `first run establishes baseline without inspecting the existing library`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(checkpoint = 0L)
        val inspector = RecordingInspector()
        val r = DedupReconciler(FakeNewFileSource(maxId = 5_000L), inspector, store, FakeGate(true), dispatcher)

        assertEquals(0, r.reconcile())
        assertEquals("checkpoint set to current max id", 5_000L, store.checkpoint)
        assertTrue("nothing inspected on baseline", inspector.seen.isEmpty())
    }

    @Test
    fun `files newer than the checkpoint are inspected and the checkpoint advances`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(checkpoint = 5_000L)
        val inspector = RecordingInspector()
        val source = FakeNewFileSource(maxId = 5_002L, files = listOf(file(5_001L), file(5_002L)))
        val r = DedupReconciler(source, inspector, store, FakeGate(true), dispatcher)

        assertEquals(2, r.reconcile())
        assertEquals(listOf(5_001L, 5_002L), inspector.seen.map { it.name.removePrefix("file").removeSuffix(".bin").toLong() })
        assertEquals(5_002L, store.checkpoint)

        // Nothing new on a second pass → no work, no re-inspection.
        inspector.seen.clear()
        assertEquals(0, r.reconcile())
        assertTrue(inspector.seen.isEmpty())
    }

    /** Gap-free pagination: 350 files across two BATCH_SIZE(200) pages are ALL inspected. */
    @Test
    fun `bulk arrival spanning multiple batches is fully paginated with no gap`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(checkpoint = 100L)
        val inspector = RecordingInspector()
        val ids = (101L..450L).toList()
        val source = FakeNewFileSource(maxId = 450L, files = ids.map { file(it) })
        val r = DedupReconciler(source, inspector, store, FakeGate(true), dispatcher)

        assertEquals(350, r.reconcile())
        assertEquals(350, inspector.seen.size)
        assertEquals(450L, store.checkpoint)
    }

    @Test
    fun `does nothing when indexing is disabled`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(enabled = false, checkpoint = 0L)
        val inspector = RecordingInspector()
        val r = DedupReconciler(FakeNewFileSource(maxId = 5_000L), inspector, store, FakeGate(true), dispatcher)

        assertEquals(0, r.reconcile())
        assertEquals(0L, store.checkpoint)
        assertTrue(inspector.seen.isEmpty())
    }

    @Test
    fun `does nothing when storage access is not granted`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(checkpoint = 0L)
        val inspector = RecordingInspector()
        val r = DedupReconciler(FakeNewFileSource(maxId = 5_000L), inspector, store, FakeGate(granted = false), dispatcher)

        assertEquals(0, r.reconcile())
        assertEquals("no baseline pinned before access", 0L, store.checkpoint)
        assertTrue(inspector.seen.isEmpty())
    }

    /** Baseline-poisoning fix: an empty/unreadable MediaStore (max id 0) must NOT pin a baseline. */
    @Test
    fun `empty or unreadable MediaStore does not poison the baseline`() = runTest(dispatcher) {
        val store = FakeCheckpointStore(checkpoint = 0L)
        val r = DedupReconciler(FakeNewFileSource(maxId = 0L), RecordingInspector(), store, FakeGate(true), dispatcher)

        assertEquals(0, r.reconcile())
        assertEquals("checkpoint stays 0, not pinned to 1", 0L, store.checkpoint)
    }
}
