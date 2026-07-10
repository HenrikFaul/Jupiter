package com.jupiter.filemanager.core.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM proof of the single storage-exclusion rule applied at every file-enumeration boundary.
 * The reported bug was Samsung My Files' `Android/.Trash/…` files showing up (in the category
 * browser, albums, search) and opening to "Not found"; these assert exactly those paths are
 * excluded, while ordinary files — and folders that merely CONTAIN the token in their name — are not.
 */
class StorageExclusionsTest {

    @Test
    fun samsungRecycleBinPathsAreExcluded() {
        val trash = "/storage/emulated/0/Android/.Trash/com.sec.android.app.myfiles/" +
            "7151d159-fc09-4db5-a7f0-a24dbdf291bf/1782424126989/storage/emulated/0/" +
            "Download/x/app-debug (6).apk"
        assertTrue(StorageExclusions.isExcluded(trash))
        // Case-insensitive: the on-disk segment is capital `.Trash`.
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/android/.trash/foo.apk"))
    }

    @Test
    fun otherExcludedDirsAreExcluded() {
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/Android/data/com.x/files/a.jpg"))
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/Android/obb/com.x/b.obb"))
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/DCIM/.thumbnails/c.jpg"))
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/Pictures/.trashed/d.jpg"))
        // A path that IS the excluded dir (ends with the segment).
        assertTrue(StorageExclusions.isExcluded("/storage/emulated/0/Android/.trash"))
    }

    @Test
    fun ordinaryPathsAreNotExcluded() {
        assertFalse(StorageExclusions.isExcluded("/storage/emulated/0/Download/report.pdf"))
        assertFalse(StorageExclusions.isExcluded("/storage/emulated/0/Pictures/IMG_2024.jpg"))
        assertFalse(StorageExclusions.isExcluded("/storage/emulated/0/DCIM/Camera/VID.mp4"))
    }

    @Test
    fun tokenInsideAFolderNameIsNotASegmentMatch() {
        // ".trash" appears in the name but is NOT a full path segment → must NOT be excluded.
        assertFalse(StorageExclusions.isExcluded("/storage/emulated/0/Documents/my.trash.notes/keep.txt"))
        assertFalse(StorageExclusions.isExcluded("/storage/emulated/0/androiddata/x.jpg"))
    }

    @Test
    fun contentUrisAndEmptyAreNotExcluded() {
        assertFalse(StorageExclusions.isExcluded("content://media/external/images/media/123"))
        assertFalse(StorageExclusions.isExcluded(""))
    }
}
