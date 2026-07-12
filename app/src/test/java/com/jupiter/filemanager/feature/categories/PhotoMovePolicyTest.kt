package com.jupiter.filemanager.feature.categories

import com.jupiter.filemanager.domain.model.FileItem
import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMovePolicyTest {

    private val root = "/storage/emulated/0"

    @Test
    fun `valid destination is delegated to repository move contract`() {
        val photo = item("$root/DCIM/Camera/photo.jpg")

        assertNull(
            PhotoMovePolicy.validationError(
                items = listOf(photo),
                destinationPath = "$root/Pictures/Archive",
                rootPath = root,
            ),
        )
    }

    @Test
    fun `same folder and out of root destinations are rejected before move`() {
        val photo = item("$root/DCIM/Camera/photo.jpg")

        assertTrue(
            PhotoMovePolicy.validationError(listOf(photo), "$root/DCIM/Camera", root)
                .orEmpty()
                .contains("already"),
        )
        assertTrue(
            PhotoMovePolicy.validationError(listOf(photo), "/data/local/tmp", root)
                .orEmpty()
                .contains("outside"),
        )
    }

    @Test
    fun `content uri cannot enter path based repository move`() {
        val mediaOnly = item("content://media/external/images/media/42")

        assertTrue(
            PhotoMovePolicy.validationError(listOf(mediaOnly), "$root/Pictures", root)
                .orEmpty()
                .contains("MediaStore"),
        )
    }

    @Test
    fun `successful old path remains suppressed despite stale media query`() {
        val moved = item("$root/DCIM/Camera/Photo.JPG")
        val untouched = item("$root/DCIM/Camera/other.jpg")
        val staleQuery = listOf(moved, untouched)

        val visible = PhotoMovePolicy.withoutSuppressedPaths(
            staleQuery,
            setOf("$root/dcim/camera/photo.jpg"),
        )

        assertEquals(listOf(untouched), visible)
    }

    @Test
    fun `parent navigation never escapes root`() {
        assertEquals(
            "$root/Pictures",
            PhotoMovePolicy.parentWithinRoot("$root/Pictures/Archive", root)
                ?.replace('\\', '/'),
        )
        assertNull(PhotoMovePolicy.parentWithinRoot(root, root))
    }

    private fun item(path: String) = FileItem(
        path = path,
        name = path.substringAfterLast('/'),
        isDirectory = false,
        sizeBytes = 10L,
        lastModified = 1L,
        type = FileType.IMAGE,
        extension = "jpg",
        mimeType = "image/jpeg",
    )
}
