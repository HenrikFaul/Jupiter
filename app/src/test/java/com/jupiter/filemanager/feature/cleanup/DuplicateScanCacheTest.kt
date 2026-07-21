package com.jupiter.filemanager.feature.cleanup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DuplicateScanCacheTest {

    private lateinit var context: Context
    private val snapshot get() = File(context.filesDir, "duplicate_scan_snapshot.tsv")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        snapshot.delete()
    }

    @After
    fun tearDown() {
        snapshot.delete()
        File(context.filesDir, "duplicate_scan_snapshot.tsv.tmp").delete()
    }

    @Test
    fun similarFlagSurvivesColdCacheReload() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val files = listOf(
            item("/storage/emulated/0/a.mp4"),
            item("/storage/emulated/0/b.mp4"),
        )
        DuplicateScanCache(context, dispatcher).saveGroups(
            listOf(DuplicateGroup("video:v2:a", files, similar = true)),
        )

        val restored = DuplicateScanCache(context, dispatcher).load()

        assertTrue(restored?.singleOrNull()?.similar == true)
    }

    @Test
    fun legacyUnversionedSnapshotIsRejected() = runTest {
        snapshot.writeText(
            "legacy\t/storage/emulated/0/a.mp4\ta.mp4\t100\t1\tVIDEO\tmp4\n" +
                "legacy\t/storage/emulated/0/b.mp4\tb.mp4\t100\t1\tVIDEO\tmp4\n",
        )

        val restored = DuplicateScanCache(
            context,
            UnconfinedTestDispatcher(testScheduler),
        ).load()

        assertNull(restored)
        assertTrue("unsafe v1 cache must be removed", !snapshot.exists())
    }

    @Test
    fun previousImageDecisionSnapshotIsRejected() = runTest {
        snapshot.writeText(
            "#jupiscan-duplicate-cache-v2-media-signature-2\n" +
                "img:old\ttrue\t/storage/emulated/0/a.jpg\ta.jpg\t100\t1\tIMAGE\tjpg\n" +
                "img:old\ttrue\t/storage/emulated/0/b.jpg\tb.jpg\t100\t1\tIMAGE\tjpg\n",
        )

        val restored = DuplicateScanCache(
            context,
            UnconfinedTestDispatcher(testScheduler),
        ).load()

        assertNull(restored)
        assertTrue("v2 image decisions must not survive the versioned gate", !snapshot.exists())
    }

    private fun item(path: String) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = 100L,
        lastModified = 1L,
        type = FileType.VIDEO,
        extension = "mp4",
    )
}
