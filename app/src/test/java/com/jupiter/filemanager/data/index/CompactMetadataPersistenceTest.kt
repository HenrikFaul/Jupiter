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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CompactMetadataPersistenceTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: FileIndexDatabase
    private lateinit var repo: FileIndexRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FileIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = FileIndexRepositoryImpl(db.fileIndexDao(), dispatcher)
    }

    @After
    fun tearDown() = db.close()

    private fun item(type: FileType = FileType.VIDEO) = FileItem(
        path = "/storage/emulated/0/Download/sample.mp4",
        name = "sample.mp4",
        isDirectory = false,
        sizeBytes = 123_456L,
        lastModified = 1_700_000_000_000L,
        type = type,
        extension = if (type == FileType.VIDEO) "mp4" else "txt",
    )

    @Test
    fun `production content hash is stored and indexed as raw blob`() = runTest(dispatcher) {
        val file = item()
        val hash = "0123456789abcdef0123456789abcdef01234567"
        repo.upsert(listOf(file))
        repo.putHash(file, hash)

        val row = db.fileIndexDao().getByPath(file.path)!!
        assertNull(row.contentHash)
        assertArrayEquals(CompactMetadataCodec.sha1ToBytes(hash), row.contentDigest)
        assertEquals(hash, repo.hashForUnchanged(file.path, file.sizeBytes, file.lastModified))
        assertEquals(1, db.fileIndexDao().byContentDigest(row.contentDigest!!).size)
    }

    @Test
    fun `media fingerprint uses compact vector extent version and geometry`() = runTest(dispatcher) {
        val file = item()
        val fingerprint = MediaFingerprint(
            hashes = List(5) { 0x1234_5678_9ABC_DEF0L xor it.toLong() },
            extent = 42_000L,
            width = 1920,
            height = 1080,
        )
        repo.upsert(listOf(file))
        repo.putMediaFingerprint(file.path, fingerprint)

        val row = db.fileIndexDao().getByPath(file.path)!!
        assertNull(row.structuralSignature)
        assertEquals(40, row.structuralSignatureBlob!!.size)
        assertEquals(42_000L, row.structuralExtent)
        assertEquals(MediaFingerprint.CURRENT_VERSION, row.structuralVersion)
        assertNotNull(row.visualGeometry)

        val decoded = MediaFingerprint.decode(
            compact = row.structuralSignatureBlob,
            legacy = row.structuralSignature,
            extent = row.structuralExtent,
            version = row.structuralVersion,
            visualGeometry = row.visualGeometry,
        )!!
        assertEquals(fingerprint, decoded)
    }

    @Test
    fun `type change preserves byte proof but invalidates type specific metadata`() = runTest(dispatcher) {
        val original = item()
        val hash = "0123456789abcdef0123456789abcdef01234567"
        repo.upsert(listOf(original))
        repo.putHash(original, hash)
        repo.putPerceptualFingerprint(
            original.path,
            dhash = 1L,
            phash = 2L,
            ahash = 3L,
            width = 4032,
            height = 3024,
        )

        repo.upsertScanned(listOf(original.copy(type = FileType.CODE, extension = "txt")), 9L)

        val row = db.fileIndexDao().getByPath(original.path)!!
        assertNotNull(row.contentDigest)
        assertNull(row.perceptualHash)
        assertNull(row.phash)
        assertNull(row.ahash)
        assertNull(row.visualGeometry)
    }

    @Test
    fun `hash refresh after external byte change atomically invalidates stale descriptors`() =
        runTest(dispatcher) {
            val original = item()
            repo.upsert(listOf(original))
            repo.putMediaFingerprint(
                original.path,
                MediaFingerprint(
                    List(5) { 0x1234_5678_9ABC_DEF0L },
                    extent = 30_000L,
                    width = 1920,
                    height = 1080,
                ),
            )

            val changed = original.copy(lastModified = original.lastModified + 2_000L)
            val newHash = "fedcba9876543210fedcba9876543210fedcba98"
            repo.putHash(changed, newHash)

            val row = db.fileIndexDao().getByPath(original.path)!!
            assertArrayEquals(CompactMetadataCodec.sha1ToBytes(newHash), row.contentDigest)
            assertNull(row.structuralHash)
            assertNull(row.structuralSignatureBlob)
            assertNull(row.structuralExtent)
            assertNull(row.visualGeometry)
        }
}
