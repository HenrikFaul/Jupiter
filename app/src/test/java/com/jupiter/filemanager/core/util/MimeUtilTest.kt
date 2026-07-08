package com.jupiter.filemanager.core.util

import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Pure JVM unit tests for [extensionOf] and [fileTypeFor] in MimeUtil.kt.
 *
 * [mimeTypeFor] is intentionally not tested here because it depends on the
 * Android framework class MimeTypeMap, which is not available on the JVM.
 */
class MimeUtilTest {

    @Before
    fun setUp() {
        // Deterministic lowercasing / formatting regardless of host locale.
        Locale.setDefault(Locale.US)
    }

    // region extensionOf ------------------------------------------------------

    @Test
    fun extensionOf_returnsLowercasedExtensionWithoutDot() {
        assertEquals("jpg", extensionOf("photo.JPG"))
        assertEquals("pdf", extensionOf("Report.Pdf"))
        assertEquals("kt", extensionOf("Main.KT"))
    }

    @Test
    fun extensionOf_simpleExtension() {
        assertEquals("txt", extensionOf("notes.txt"))
    }

    @Test
    fun extensionOf_usesOnlySubstringAfterFinalDot() {
        assertEquals("gz", extensionOf("archive.tar.gz"))
    }

    @Test
    fun extensionOf_returnsEmptyWhenNoDot() {
        assertEquals("", extensionOf("README"))
    }

    @Test
    fun extensionOf_dotfileHasNoExtension() {
        // Leading dot is not an extension separator.
        assertEquals("", extensionOf(".gitignore"))
    }

    @Test
    fun extensionOf_trailingDotHasNoExtension() {
        assertEquals("", extensionOf("name."))
    }

    @Test
    fun extensionOf_emptyStringReturnsEmpty() {
        assertEquals("", extensionOf(""))
    }

    @Test
    fun extensionOf_stripsDirectoryComponents() {
        assertEquals("png", extensionOf("/storage/emulated/0/pic.png"))
    }

    @Test
    fun extensionOf_dotfileWithRealExtension() {
        // The leading dot is ignored; the final dot delimits the extension.
        assertEquals("yml", extensionOf(".config.yml"))
    }

    // endregion

    // region fileTypeFor: directory ------------------------------------------

    @Test
    fun fileTypeFor_directoryAlwaysFolder() {
        assertEquals(FileType.FOLDER, fileTypeFor("anything.jpg", isDirectory = true))
        assertEquals(FileType.FOLDER, fileTypeFor("noext", isDirectory = true))
    }

    // endregion

    // region fileTypeFor: extension buckets ----------------------------------

    @Test
    fun fileTypeFor_imageBucket() {
        assertEquals(FileType.IMAGE, fileTypeFor("photo.jpg", isDirectory = false))
    }

    @Test
    fun fileTypeFor_videoBucket() {
        assertEquals(FileType.VIDEO, fileTypeFor("clip.mp4", isDirectory = false))
    }

    @Test
    fun fileTypeFor_audioBucket() {
        assertEquals(FileType.AUDIO, fileTypeFor("song.mp3", isDirectory = false))
    }

    @Test
    fun fileTypeFor_pdfBucket() {
        assertEquals(FileType.PDF, fileTypeFor("doc.pdf", isDirectory = false))
    }

    @Test
    fun fileTypeFor_archiveBucket() {
        assertEquals(FileType.ARCHIVE, fileTypeFor("data.zip", isDirectory = false))
        assertEquals(FileType.ARCHIVE, fileTypeFor("data.rar", isDirectory = false))
        assertEquals(FileType.ARCHIVE, fileTypeFor("data.7z", isDirectory = false))
    }

    @Test
    fun fileTypeFor_apkBucket() {
        assertEquals(FileType.APK, fileTypeFor("app.apk", isDirectory = false))
    }

    @Test
    fun fileTypeFor_codeBucket() {
        assertEquals(FileType.CODE, fileTypeFor("Main.kt", isDirectory = false))
        assertEquals(FileType.CODE, fileTypeFor("Main.java", isDirectory = false))
    }

    @Test
    fun fileTypeFor_documentBucket() {
        assertEquals(FileType.DOCUMENT, fileTypeFor("notes.txt", isDirectory = false))
        assertEquals(FileType.DOCUMENT, fileTypeFor("letter.doc", isDirectory = false))
    }

    @Test
    fun fileTypeFor_unknownExtensionIsOther() {
        assertEquals(FileType.OTHER, fileTypeFor("mystery.qwerty", isDirectory = false))
    }

    @Test
    fun fileTypeFor_noExtensionIsOther() {
        assertEquals(FileType.OTHER, fileTypeFor("README", isDirectory = false))
    }

    @Test
    fun fileTypeFor_isCaseInsensitive() {
        // extensionOf lowercases, so uppercase extensions still classify.
        assertEquals(FileType.IMAGE, fileTypeFor("PHOTO.JPG", isDirectory = false))
    }

    // endregion
}
