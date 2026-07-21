package com.jupiter.filemanager.data.index

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    /** Deterministic low-contrast scene that stresses dHash under strong JPEG quantization. */
    private fun drawLowContrastScene(width: Int = 256, height: Int = 192): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val z = 128.0 +
                    13.586677127187162 * (x / width.toDouble() - 0.5) +
                    6.5352800102059305 * (y / height.toDouble() - 0.5) +
                    7.216226687563077 * (
                    0.35 * kotlin.math.sin(x / 27.109571163552427 + 0.3794166497192111) +
                        0.25 * kotlin.math.cos(y / 46.89223417257611 + 0.9052822969632635) +
                        0.18 * kotlin.math.sin(
                        (x + y) / 18.370005124933797 + 6.183737509116225,
                    )
                    )
                val pseudoRandom = (
                    (x.toLong() * 73_856_093L) xor
                        (y.toLong() * 19_349_663L) xor 1_655_700_368L
                    ) and 0xffffL
                val noise = (pseudoRandom / 65_535.0 * 2.0 - 1.0) * 0.23228547955783904
                fun channel(offset: Double): Int = (z + noise + offset).toInt().coerceIn(0, 255)
                bitmap.setPixel(
                    x,
                    y,
                    Color.rgb(channel(-4.984589402312542), channel(2.273848874927401), channel(-0.39370797785409106)),
                )
            }
        }
        return bitmap
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
            val stackPng = source.computeAll(png.absolutePath)!!
            val stackJpeg = source.computeAll(jpegSmall.absolutePath)!!
            val stackInverted = source.computeAll(invertedPng.absolutePath)!!

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
            assertTrue(
                "the bounded production stack must retain the PNG/JPEG resize positive",
                PerceptualHash.isSamePicture(
                    stackPng.dhash, stackJpeg.dhash,
                    stackPng.phash, stackJpeg.phash,
                    stackPng.ahash, stackJpeg.ahash,
                    stackPng.visualGeometry, stackJpeg.visualGeometry,
                ),
            )
            assertFalse(
                "the bounded production stack must reject the inverted hard negative",
                PerceptualHash.isSamePicture(
                    stackPng.dhash, stackInverted.dhash,
                    stackPng.phash, stackInverted.phash,
                    stackPng.ahash, stackInverted.ahash,
                    stackPng.visualGeometry, stackInverted.visualGeometry,
                ),
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

    @Test
    fun lowContrastStrongJpegReencodeRetainsThreeFamilyConsensus() {
        val dir = java.nio.file.Files.createTempDirectory("jupiter-phash-smooth").toFile()
        try {
            val png = File(dir, "smooth.png")
            val jpeg = File(dir, "smooth-q50.jpg")
            val scene = drawLowContrastScene()
            try {
                save(scene, png, Bitmap.CompressFormat.PNG, 100)
                save(scene, jpeg, Bitmap.CompressFormat.JPEG, 50)
            } finally {
                scene.recycle()
            }

            val original = source.computeAll(png.absolutePath)!!
            val reencoded = source.computeAll(jpeg.absolutePath)!!
            val evidence = PerceptualHash.samePictureEvidence(
                original.dhash, reencoded.dhash,
                original.phash, reencoded.phash,
                original.ahash, reencoded.ahash,
                original.visualGeometry, reencoded.visualGeometry,
            )

            assertTrue(
                "low-contrast q50 re-encode must stay reviewable " +
                    "(d=${evidence.dHashDistance}, p=${evidence.pHashDistance}, " +
                    "a=${evidence.aHashDistance}, score=${evidence.combinedScore})",
                evidence.matches,
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}
