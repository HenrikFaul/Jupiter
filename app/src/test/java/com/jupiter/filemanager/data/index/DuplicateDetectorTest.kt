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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Robolectric + in-memory Room test of [DuplicateDetector] over REAL files, asserting directly on
 * `onFileArrived`'s return value (no SharedFlow-collection timing). This proves the exact scenario
 * the user hit: a byte-identical copy of a file whose original was indexed with METADATA ONLY (no
 * hash — the realistic post-survey state) is detected as an EXACT duplicate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuplicateDetectorTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl
    private lateinit var detector: DuplicateDetector
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
        detector = DuplicateDetector(
            ctx, repo, PerceptualHashSource(), StructuralFingerprintSource(),
            FakeMediaFingerprintSource(), db.dedupDecisionDao(), dispatcher,
        )
        tempDir = java.nio.file.Files.createTempDirectory("jupiter-detector").toFile()
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
    fun exactByteIdenticalCopyIsDetectedAgainstAMetadataOnlyOriginal() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 251).toByte() }
        val original = File(tempDir, "photo.jpg").apply { writeBytes(payload) }
        repo.indexFile(item(original)) // survey-style: metadata only, no hash

        val copy = File(tempDir, "photo (1).jpg").apply { writeBytes(payload) }

        val alert = detector.onFileArrived(item(copy))

        assertEquals(DuplicateKind.EXACT, alert?.kind)
        assertEquals(copy.absolutePath, alert?.newFile?.path)
        assertTrue(
            "the alert points at the pre-existing original",
            alert?.existing?.any { it.path == original.absolutePath } == true,
        )
    }

    @Test
    fun sameArrivalDecisionIsPersistentlySuppressedOnSecondPass() = runTest(dispatcher) {
        val payload = ByteArray(9000) { (it % 199).toByte() }
        val original = File(tempDir, "report.pdf").apply { writeBytes(payload) }
        val copy = File(tempDir, "report (1).pdf").apply { writeBytes(payload) }
        repo.indexFile(item(original))

        assertEquals(DuplicateKind.EXACT, detector.onFileArrived(item(copy))?.kind)
        assertNull("the canonical pair decision is stored, so restart/replay does not alert again",
            detector.onFileArrived(item(copy)),
        )
    }

    @Test
    fun blockedNotificationRemainsPendingAndIsDeliveredByLaterSummaryRetry() = runTest(dispatcher) {
        val publisher = RecordingNotificationPublisher(
            alertResult = NotificationDeliveryResult(
                NotificationDeliveryStatus.BLOCKED,
                "permission denied",
            ),
        )
        detector = detectorWith(publisher)
        val payload = ByteArray(9000) { (it % 197).toByte() }
        val original = File(tempDir, "blocked.png").apply { writeBytes(payload) }
        val copy = File(tempDir, "blocked (1).png").apply { writeBytes(payload) }
        repo.indexFile(item(original))

        assertEquals(DuplicateKind.EXACT, detector.onFileArrived(item(copy))?.kind)
        val pending = db.dedupDecisionDao().deliveryCandidates(Long.MAX_VALUE, 10).single()
        assertEquals(DedupDeliveryState.BLOCKED.name, pending.deliveryState)
        assertEquals(1, pending.deliveryAttempts)
        assertEquals("permission denied", pending.lastDeliveryFailure)

        publisher.summaryResult = NotificationDeliveryResult(NotificationDeliveryStatus.DELIVERED)
        detector.retryPendingNotifications()

        val delivered = db.dedupDecisionDao().get(pending.decisionKey)
        assertEquals(DedupDeliveryState.DELIVERED.name, delivered?.deliveryState)
        assertEquals(2, delivered?.deliveryAttempts)
        assertTrue((delivered?.deliveredAt ?: 0L) > 0L)
        assertEquals(1, publisher.summaryCalls)
    }

    @Test
    fun failedPublishIsNotMistakenForDeliveredAndCanBeRetried() = runTest(dispatcher) {
        val publisher = RecordingNotificationPublisher(
            alertResult = NotificationDeliveryResult(
                NotificationDeliveryStatus.FAILED,
                "notification manager failure",
            ),
        )
        detector = detectorWith(publisher)
        val payload = ByteArray(9000) { (it % 193).toByte() }
        val original = File(tempDir, "failed.jpg").apply { writeBytes(payload) }
        val copy = File(tempDir, "failed (1).jpg").apply { writeBytes(payload) }
        repo.indexFile(item(original))

        detector.onFileArrived(item(copy))
        val failed = db.dedupDecisionDao().deliveryCandidates(Long.MAX_VALUE, 10).single()
        assertEquals(DedupDeliveryState.FAILED.name, failed.deliveryState)

        publisher.summaryResult = NotificationDeliveryResult(NotificationDeliveryStatus.DELIVERED)
        detector.retryPendingNotifications()

        assertEquals(
            DedupDeliveryState.DELIVERED.name,
            db.dedupDecisionDao().get(failed.decisionKey)?.deliveryState,
        )
    }

    @Test
    fun aUniqueFileProducesNoAlert() = runTest(dispatcher) {
        val unique = File(tempDir, "unique.bin").apply { writeBytes(ByteArray(9000) { it.toByte() }) }
        assertNull(detector.onFileArrived(item(unique)))
    }

    @Test
    fun transientFingerprintFailureIsRetryNotUnique() = runTest(dispatcher) {
        val missingCode = FileItem(
            path = File(tempDir, "temporarily-unreadable.kt").absolutePath,
            name = "temporarily-unreadable.kt",
            isDirectory = false,
            sizeBytes = 9000,
            lastModified = System.currentTimeMillis(),
            type = FileType.CODE,
            extension = "kt",
        )

        val result = detector.inspectArrival(missingCode)

        assertTrue(
            "a transient source null must not advance the durable checkpoint as UNIQUE",
            result is ArrivalInspectionResult.Retry,
        )
    }

    @Test
    fun tinyFilesNeverAlert() = runTest(dispatcher) {
        // Below the 4 KiB alert floor: empty placeholders must never spam alerts.
        val a = File(tempDir, "a.nomedia").apply { writeBytes(ByteArray(0)) }
        val b = File(tempDir, "b.nomedia").apply { writeBytes(ByteArray(0)) }
        repo.indexFile(item(a))
        assertNull(detector.onFileArrived(item(b)))
    }

    private fun detectorWith(publisher: DuplicateNotificationPublisher) = DuplicateDetector(
        ApplicationProvider.getApplicationContext(),
        repo,
        PerceptualHashSource(),
        StructuralFingerprintSource(),
        FakeMediaFingerprintSource(),
        db.dedupDecisionDao(),
        publisher,
        dispatcher,
    )

    private class RecordingNotificationPublisher(
        var alertResult: NotificationDeliveryResult,
        var summaryResult: NotificationDeliveryResult = alertResult,
    ) : DuplicateNotificationPublisher {
        var alertCalls = 0
        var summaryCalls = 0

        override fun health() = DuplicateNotificationHealth(
            runtimePermissionGranted = true,
            applicationNotificationsEnabled = true,
            channelEnabled = true,
        )

        override fun publish(alert: DuplicateAlert): NotificationDeliveryResult {
            alertCalls++
            return alertResult
        }

        override fun publishPendingSummary(
            decisions: List<DedupDecision>,
        ): NotificationDeliveryResult {
            summaryCalls++
            return summaryResult
        }
    }
}
