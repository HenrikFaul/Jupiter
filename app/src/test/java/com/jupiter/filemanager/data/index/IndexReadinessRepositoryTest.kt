package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import com.jupiter.filemanager.domain.model.PipelineStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class IndexReadinessRepositoryTest {

    private lateinit var db: FileIndexDatabase
    private lateinit var index: FileIndexRepositoryImpl
    private lateinit var readiness: IndexReadinessRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, FileIndexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        index = FileIndexRepositoryImpl(db.fileIndexDao(), Dispatchers.IO)
        readiness = IndexReadinessRepositoryImpl(
            db.indexStateDao(),
            db.fileIndexDao(),
            Dispatchers.IO,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun descriptorUpdateEmitsReadinessWithoutAnIndexStateWrite() = runBlocking {
        val image = FileItem(
            path = "/storage/emulated/0/DCIM/live.jpg",
            name = "live.jpg",
            isDirectory = false,
            sizeBytes = 100,
            lastModified = 1,
            type = FileType.IMAGE,
            extension = "jpg",
        )
        index.upsert(listOf(image))
        db.indexStateDao().upsert(
            IndexState(
                volumeId = IndexState.PRIMARY_VOLUME,
                metadataStatus = IndexStatus.COMPLETE.name,
                lastCompleteGeneration = 1,
                filesSeen = 1,
            ),
        )
        assertEquals(0L, readiness.currentReadiness().imageDescriptors.ready)

        val observerReady = CompletableDeferred<Unit>()
        val updated = async {
            withTimeout(5_000) {
                readiness.observeReadiness()
                    .onEach {
                        if (!observerReady.isCompleted) observerReady.complete(Unit)
                    }
                    .first { it.imageDescriptors.ready == 1L }
            }
        }
        observerReady.await()
        index.putPerceptualFingerprint(image.path, 1L, 2L, 3L, 1920, 1080)

        val emitted = updated.await()
        assertEquals(1L, emitted.imageDescriptors.total)
        assertEquals(PipelineStatus.COMPLETE, emitted.imageDescriptors.state)
    }

    @Test
    fun oneDescriptorPageMovesLiveCoverageFromZeroToComplete() = runBlocking {
        val images = (0 until 100).map { index ->
            FileItem(
                path = "/storage/emulated/0/DCIM/page-$index.jpg",
                name = "page-$index.jpg",
                isDirectory = false,
                sizeBytes = 100,
                lastModified = 1,
                type = FileType.IMAGE,
                extension = "jpg",
            )
        }
        index.upsert(images)
        assertEquals(0L, readiness.currentReadiness().imageDescriptors.ready)

        index.putPerceptualFingerprints(
            images.mapIndexed { position, image ->
                PathPerceptualFingerprint(
                    image.path,
                    PerceptualFingerprint(
                        dhash = position.toLong(),
                        phash = position.toLong(),
                        ahash = position.toLong(),
                        width = 1920,
                        height = 1080,
                    ),
                )
            },
        )

        val coverage = readiness.currentReadiness().imageDescriptors
        assertEquals(100L, coverage.ready)
        assertEquals(100L, coverage.total)
        assertEquals(PipelineStatus.COMPLETE, coverage.state)
    }
}
