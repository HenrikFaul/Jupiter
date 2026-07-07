package com.jupiter.filemanager.data.index

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Robolectric round-trip proof of the USER-FACING contract: the SAME picture saved in a
 * different format AND resolution (PNG 240×180 vs JPEG 120×90 q70) produces near-identical
 * dHashes, while a structurally different picture does not. Runs under `testDebugUnitTest`
 * via Robolectric's native graphics runtime (real BitmapFactory decoding, no emulator).
 *
 * NATIVE graphics is forced explicitly: under the default legacy shadows BitmapFactory
 * fabricates stub bitmaps (any path "decodes" to a fake 100×100 with zeroed pixels), which
 * both breaks these assertions and proves nothing about real decoding.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], application = Application::class)
class PerceptualHashSourceTest {

    private val source = PerceptualHashSource()

    /** Draws a recognizable diagonal-gradient "photo" onto a bitmap. */
    private fun drawScene(width: Int, height: Int, inverted: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val colors = if (inverted) {
            intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        } else {
            intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        }
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                colors[0], colors[1], Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun save(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat, quality: Int) {
        file.outputStream().use { bitmap.compress(format, quality, it) }
    }

    @Test
    fun samePictureAcrossFormatAndResolutionIsNear_differentPictureIsNot() {
        val dir = java.nio.file.Files.createTempDirectory("jupiter-phash").toFile()
        try {
            val png = File(dir, "scene.png")
            val jpegSmall = File(dir, "scene_small.jpg")
            val invertedPng = File(dir, "inverted.png")

            save(drawScene(240, 180, inverted = false), png, Bitmap.CompressFormat.PNG, 100)
            save(drawScene(120, 90, inverted = false), jpegSmall, Bitmap.CompressFormat.JPEG, 70)
            save(drawScene(240, 180, inverted = true), invertedPng, Bitmap.CompressFormat.PNG, 100)

            val hashPng = source.compute(png.absolutePath)
            val hashJpeg = source.compute(jpegSmall.absolutePath)
            val hashInverted = source.compute(invertedPng.absolutePath)

            assertNotNull(hashPng)
            assertNotNull(hashJpeg)
            assertNotNull(hashInverted)
            assertTrue("decodable image must not be UNHASHABLE", hashPng != PerceptualHash.UNHASHABLE)

            assertTrue(
                "same picture across format+resolution must be near " +
                    "(distance=${PerceptualHash.hammingDistance(hashPng!!, hashJpeg!!)})",
                PerceptualHash.isNear(hashPng, hashJpeg),
            )
            assertFalse(
                "inverted picture must NOT be near",
                PerceptualHash.isNear(hashPng, hashInverted!!),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun undecodableFileIsMarkedUnhashable() {
        val dir = java.nio.file.Files.createTempDirectory("jupiter-phash2").toFile()
        try {
            val notAnImage = File(dir, "notes.jpg").apply { writeText("this is not an image") }
            val hash = source.compute(notAnImage.absolutePath)
            assertTrue(hash == PerceptualHash.UNHASHABLE)
        } finally {
            dir.deleteRecursively()
        }
    }
}
