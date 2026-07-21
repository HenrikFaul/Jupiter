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
        assertEquals(0, row.perceptualVersion)
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
            assertEquals(0, row.perceptualVersion)
        }


    @Test
    fun `complete image stack is tagged with the current descriptor version`() = runTest(dispatcher) {
        val image = item(FileType.IMAGE).copy(
            path = "/storage/emulated/0/DCIM/photo.jpg",
            name = "photo.jpg",
            extension = "jpg",
        )
        repo.upsert(listOf(image))
        repo.putPerceptualFingerprint(image.path, 1L, 2L, 3L, width = 4032, height = 3024)

        val row = db.fileIndexDao().getByPath(image.path)!!
        assertEquals(PerceptualHash.CURRENT_DESCRIPTOR_VERSION, row.perceptualVersion)
        assertNotNull(row.visualGeometry)
        assertEquals(0, repo.imagesNeedingPerceptualHashCount())
    }

    @Test
    fun `legacy dHash only write fails closed and remains queued for the full stack`() =
        runTest(dispatcher) {
            val image = item(FileType.IMAGE).copy(
                path = "/storage/emulated/0/DCIM/legacy.jpg",
                name = "legacy.jpg",
                extension = "jpg",
            )
            repo.upsert(listOf(image))
            repo.putPerceptualHash(image.path, 123L)

            val row = db.fileIndexDao().getByPath(image.path)!!
            assertEquals(0, row.perceptualVersion)
            assertNull(row.phash)
            assertNull(row.ahash)
            assertEquals(listOf(image.path), repo.imagesNeedingPerceptualHash(10).map { it.path })
        }

    @Test
    fun `only complete or all-unhashable stacks are ready and every other state is requeued`() =
        runTest(dispatcher) {
            val names = listOf(
                "mixed-d", "mixed-p", "mixed-a", "all-unhashable",
                "complete", "missing-geometry", "obsolete-version",
            )
            val images = names.associateWith { name ->
                item(FileType.IMAGE).copy(
                    path = "/storage/emulated/0/DCIM/$name.jpg",
                    name = "$name.jpg",
                    extension = "jpg",
                )
            }
            repo.upsert(images.values.toList())
            val sentinel = PerceptualHash.UNHASHABLE
            val geometry = CompactMetadataCodec.packDimensions(1920, 1080)
            val dao = db.fileIndexDao()
            suspend fun write(
                name: String,
                d: Long,
                p: Long,
                a: Long,
                packedGeometry: Long?,
                version: Int = PerceptualHash.CURRENT_DESCRIPTOR_VERSION,
            ) = dao.updatePerceptualFingerprint(
                images.getValue(name).path, d, p, a, packedGeometry, version,
            )

            write("mixed-d", sentinel, 2L, 3L, geometry)
            write("mixed-p", 1L, sentinel, 3L, geometry)
            write("mixed-a", 1L, 2L, sentinel, geometry)
            write("all-unhashable", sentinel, sentinel, sentinel, null)
            write("complete", 1L, 2L, 3L, geometry)
            write("missing-geometry", 1L, 2L, 3L, null)
            write("obsolete-version", 1L, 2L, 3L, geometry, version = 0)

            val missing = repo.imagesNeedingPerceptualHash(20).map { it.path }.toSet()
            val ready = dao.countImagesWithDescriptors(
                FileType.IMAGE.name,
                PerceptualHash.CURRENT_DESCRIPTOR_VERSION,
                sentinel,
            )
            assertEquals(2, ready)
            assertEquals(5, repo.imagesNeedingPerceptualHashCount())
            assertEquals(images.size, ready + missing.size)
            assertEquals(
                setOf("mixed-d", "mixed-p", "mixed-a", "missing-geometry", "obsolete-version")
                    .map { images.getValue(it).path }
                    .toSet(),
                missing,
            )
        }

    @Test
    fun `descriptor page is persisted through the batch contract`() = runTest(dispatcher) {
        val images = (1..2).map { number ->
            item(FileType.IMAGE).copy(
                path = "/storage/emulated/0/DCIM/batch-$number.jpg",
                name = "batch-$number.jpg",
                extension = "jpg",
            )
        }
        repo.upsert(images)

        repo.putPerceptualFingerprints(
            images.mapIndexed { index, image ->
                PathPerceptualFingerprint(
                    image.path,
                    PerceptualFingerprint(
                        dhash = index.toLong() + 1,
                        phash = index.toLong() + 2,
                        ahash = index.toLong() + 3,
                        width = 1920,
                        height = 1080,
                    ),
                )
            },
        )

        assertEquals(0, repo.imagesNeedingPerceptualHashCount())
        images.forEach { image ->
            assertEquals(
                PerceptualHash.CURRENT_DESCRIPTOR_VERSION,
                db.fileIndexDao().getByPath(image.path)!!.perceptualVersion,
            )
        }
    }

    @Test
    fun `image backlog keyset advances beyond a fully retryable first page`() = runTest(dispatcher) {
        val images = (0 until 205).map { index ->
            item(FileType.IMAGE).copy(
                path = "/storage/emulated/0/DCIM/${index.toString().padStart(3, '0')}.jpg",
                name = "${index.toString().padStart(3, '0')}.jpg",
                extension = "jpg",
            )
        }
        repo.upsert(images)

        val first = repo.imagesNeedingPerceptualHash(100)
        val second = repo.imagesNeedingPerceptualHash(100, first.last().path)
        val third = repo.imagesNeedingPerceptualHash(100, second.last().path)

        assertEquals(100, first.size)
        assertEquals(100, second.size)
        assertEquals(5, third.size)
        assertEquals(205, (first + second + third).map { it.path }.toSet().size)
    }
}
