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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class CompactMetadataMigrationTest {

    private lateinit var helper: SupportSQLiteOpenHelper
    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(9) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE file_index (" +
                                    "path TEXT NOT NULL PRIMARY KEY, contentHash TEXT, " +
                                    "quickHash TEXT, structuralSignature TEXT, " +
                                    "structuralVersion INTEGER NOT NULL DEFAULT 0)",
                            )
                            db.execSQL(
                                "CREATE INDEX index_file_index_contentHash " +
                                    "ON file_index(contentHash)",
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
    fun `v9 migration compacts known values and preserves unknown legacy tokens`() {
        val sha1 = "0123456789abcdef0123456789abcdef01234567"
        val signature = List(5) { "123456789abcdef${it}" }.joinToString(",")
        db.execSQL(
            "INSERT INTO file_index(path,contentHash,quickHash,structuralSignature,structuralVersion) " +
                "VALUES('/valid', '$sha1', '123:$sha1', '$signature', 2)",
        )
        db.execSQL(
            "INSERT INTO file_index(path,contentHash,quickHash,structuralSignature,structuralVersion) " +
                "VALUES('/legacy', 'custom-token', 'bad-quick', 'bad-vector', 2)",
        )

        IndexModule.MIGRATION_9_10.migrate(db)

        db.query(
            "SELECT contentHash,contentDigest,quickHash,quickDigest," +
                "structuralSignature,structuralSignatureBlob FROM file_index WHERE path='/valid'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(20, cursor.getBlob(1).size)
            assertTrue(cursor.isNull(2))
            assertEquals(20, cursor.getBlob(3).size)
            assertTrue(cursor.isNull(4))
            assertEquals(40, cursor.getBlob(5).size)
        }

        db.query(
            "SELECT contentHash,contentDigest,quickHash,quickDigest," +
                "structuralSignature,structuralSignatureBlob FROM file_index WHERE path='/legacy'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("custom-token", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("bad-quick", cursor.getString(2))
            assertTrue(cursor.isNull(3))
            assertEquals("bad-vector", cursor.getString(4))
            assertTrue(cursor.isNull(5))
        }

        val indices = mutableSetOf<String>()
        db.query("SELECT name FROM sqlite_master WHERE type='index'").use { cursor ->
            while (cursor.moveToNext()) indices += cursor.getString(0)
        }
        assertTrue("compact digest must have the hot lookup index", "index_file_index_contentDigest" in indices)
        assertFalse("retired TEXT index wastes pages", "index_file_index_contentHash" in indices)
    }
}
