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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Real Room proof of the file-identity NORMALIZATION fix: MediaStore reports whole-second
 * mtimes (`DATE_MODIFIED` × 1000) while a filesystem stat reports raw millis, so the SAME
 * untouched file arrives with two "different" mtimes depending on which enumerator indexed
 * it. Identity comparison is now second-precision — a sub-second rounding difference must
 * NEVER wipe the cached content/perceptual fingerprints (which previously forced whole-library
 * re-hash/re-decode), while a real (≥ 1 s) modification must still clear them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IdentityPrecisionTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, FileIndexDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(mtime: Long) = FileItem(
        path = "/s/photo.jpg",
        name = "photo.jpg",
        isDirectory = false,
        sizeBytes = 1234L,
        lastModified = mtime,
        type = FileType.IMAGE,
        extension = "jpg",
    )

    @Test
    fun subSecondMtimeRoundingPreservesAllFingerprints() = runTest(dispatcher) {
        // Seed as MediaStore would: whole-second mtime; then fingerprint it.
        val seeded = item(mtime = 1_700_000_000_000L)
        repo.upsert(listOf(seeded))
        repo.putHash(seeded, "content-hash")
        repo.putPerceptualFingerprint(seeded.path, dhash = 11L, phash = 22L, ahash = 33L)

        // Re-index as a filesystem stat would: same second, raw millis (+ 777 ms).
        repo.upsertScanned(listOf(item(mtime = 1_700_000_000_777L)), generation = 7L)

        val row = db.fileIndexDao().getByPath(seeded.path)!!
        assertEquals("content hash must survive rounding", "content-hash", row.contentHash)
        assertEquals("dHash must survive rounding", 11L, row.perceptualHash)
        assertEquals("pHash must survive rounding", 22L, row.phash)
        assertEquals("aHash must survive rounding", 33L, row.ahash)
        assertEquals("generation must be re-stamped", 7L, row.lastSeenGeneration)
    }

    @Test
    fun aRealModificationStillClearsTheFingerprints() = runTest(dispatcher) {
        val seeded = item(mtime = 1_700_000_000_000L)
        repo.upsert(listOf(seeded))
        repo.putHash(seeded, "content-hash")
        repo.putPerceptualFingerprint(seeded.path, dhash = 11L, phash = 22L, ahash = 33L)

        // The file was actually edited: mtime moved by 2 whole seconds.
        repo.upsertScanned(listOf(item(mtime = 1_700_000_002_000L)), generation = 8L)

        val row = db.fileIndexDao().getByPath(seeded.path)!!
        assertNull("content hash must be invalidated", row.contentHash)
        assertNull("dHash must be invalidated", row.perceptualHash)
        assertNull("pHash must be invalidated", row.phash)
        assertNull("aHash must be invalidated", row.ahash)
    }

    @Test
    fun hashIfUnchangedMatchesAcrossMtimePrecisions() = runTest(dispatcher) {
        val seeded = item(mtime = 1_700_000_000_000L) // whole-second (MediaStore style)
        repo.upsert(listOf(seeded))
        repo.putHash(seeded, "abc")

        // Query with the raw-millis stat value of the same second → the cached hash is reusable.
        assertEquals(
            "abc",
            repo.hashForUnchanged(seeded.path, seeded.sizeBytes, 1_700_000_000_654L),
        )
        // A different second → not reusable.
        assertNull(repo.hashForUnchanged(seeded.path, seeded.sizeBytes, 1_700_000_003_000L))
    }
}
