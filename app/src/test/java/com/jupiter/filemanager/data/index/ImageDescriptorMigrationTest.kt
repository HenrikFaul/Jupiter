package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.di.IndexModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class ImageDescriptorMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(10) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE file_index (path TEXT NOT NULL PRIMARY KEY, " +
                                    "isDirectory INTEGER NOT NULL, typeName TEXT NOT NULL, " +
                                    "perceptualHash INTEGER, phash INTEGER, ahash INTEGER, " +
                                    "visualGeometry INTEGER, contentDigest BLOB, " +
                                    "structuralVersion INTEGER NOT NULL DEFAULT 0)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        db = helper.writableDatabase
    }

    @After
    fun tearDown() = helper.close()

    @Test
    fun `v10 to v11 preserves proofs and requeues only incomplete image descriptors`() {
        val sentinel = PerceptualHash.UNHASHABLE
        db.execSQL(
            "INSERT INTO file_index VALUES('/ready',0,'IMAGE',1,2,3,?,X'010203',0)",
            arrayOf(CompactMetadataCodec.packDimensions(1920, 1080)),
        )
        db.execSQL("INSERT INTO file_index VALUES('/legacy',0,'IMAGE',4,5,6,NULL,X'040506',0)")
        db.execSQL(
            "INSERT INTO file_index VALUES('/bad',0,'IMAGE',?,?,?,NULL,X'070809',0)",
            arrayOf(sentinel, sentinel, sentinel),
        )
        db.execSQL(
            "INSERT INTO file_index VALUES('/video',0,'VIDEO',NULL,NULL,NULL,?,X'0A0B0C',2)",
            arrayOf(CompactMetadataCodec.packDimensions(3840, 2160)),
        )

        IndexModule.MIGRATION_10_11.migrate(db)

        val versions = linkedMapOf<String, Int>()
        db.query("SELECT path,perceptualVersion FROM file_index ORDER BY path").use { cursor ->
            while (cursor.moveToNext()) versions[cursor.getString(0)] = cursor.getInt(1)
        }
        assertEquals(1, versions["/ready"])
        assertEquals(0, versions["/legacy"])
        assertEquals(1, versions["/bad"])
        assertEquals(0, versions["/video"])

        db.query("SELECT contentDigest,structuralVersion FROM file_index WHERE path='/video'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getBlob(0).size)
                assertEquals(2, cursor.getInt(1))
            }
    }
}
