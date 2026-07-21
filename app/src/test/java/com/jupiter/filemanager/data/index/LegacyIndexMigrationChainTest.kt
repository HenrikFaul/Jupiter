package com.jupiter.filemanager.data.index

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.jupiter.filemanager.di.IndexModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Proves that the oldest shipped v1 cache opens through the complete non-destructive v11 chain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class LegacyIndexMigrationChainTest {

    private lateinit var context: Context
    private val databaseName = "legacy-jupiscan-index.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `v1 row survives and Room validates the final v11 schema`() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(databaseName)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE IF NOT EXISTS `file_index` (" +
                                    "`path` TEXT NOT NULL, `parentPath` TEXT NOT NULL, " +
                                    "`name` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                                    "`lastModified` INTEGER NOT NULL, `typeName` TEXT NOT NULL, " +
                                    "`isDirectory` INTEGER NOT NULL, `extension` TEXT NOT NULL, " +
                                    "`contentHash` TEXT, `indexedAt` INTEGER NOT NULL, " +
                                    "PRIMARY KEY(`path`))",
                            )
                            db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_file_index_parentPath` " +
                                    "ON `file_index` (`parentPath`)",
                            )
                            db.execSQL(
                                "CREATE INDEX IF NOT EXISTS `index_file_index_name` " +
                                    "ON `file_index` (`name`)",
                            )
                            db.execSQL(
                                "INSERT INTO file_index VALUES(" +
                                    "'/storage/emulated/0/DCIM/legacy.jpg'," +
                                    "'/storage/emulated/0/DCIM','legacy.jpg',123,456,'IMAGE',0," +
                                    "'jpg','0123456789abcdef0123456789abcdef01234567',789)",
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
        helper.writableDatabase
        helper.close()

        val migrated = Room.databaseBuilder(context, FileIndexDatabase::class.java, databaseName)
            .addMigrations(*IndexModule.allMigrations())
            .allowMainThreadQueries()
            .build()
        try {
            val row = runBlocking {
                migrated.fileIndexDao().getByPath("/storage/emulated/0/DCIM/legacy.jpg")
            }
            assertNotNull(row)
            assertEquals(20, row!!.contentDigest!!.size)
            assertNull(row.contentHash)
            assertEquals(0, row.perceptualVersion)
            assertEquals(0L, row.lastSeenGeneration)
        } finally {
            migrated.close()
        }
    }
}
