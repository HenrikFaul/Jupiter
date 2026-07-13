package com.jupiter.filemanager.data.index

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadIndexObserverTest {

    @Test
    fun `temporary producer names are ignored until final rename`() {
        assertTrue(DownloadIndexObserver.isTemporaryDownloadName("photo.jpg.crdownload"))
        assertTrue(DownloadIndexObserver.isTemporaryDownloadName("PHOTO.JPG.PART"))
        assertTrue(DownloadIndexObserver.isTemporaryDownloadName("export.partial"))
        assertTrue(DownloadIndexObserver.isTemporaryDownloadName("video.download"))
    }

    @Test
    fun `ordinary final names remain observable`() {
        assertFalse(DownloadIndexObserver.isTemporaryDownloadName("photo.jpg"))
        assertFalse(DownloadIndexObserver.isTemporaryDownloadName("document.pdf"))
        assertFalse(DownloadIndexObserver.isTemporaryDownloadName("important.tmp"))
    }
}
