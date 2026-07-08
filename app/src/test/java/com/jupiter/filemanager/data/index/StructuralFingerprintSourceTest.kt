package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.data.index.dedup.TextSimHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Pure-JVM proof of the non-perceptual near-duplicate extractors:
 *  - text/code SimHash recognises a reformatted copy and rejects unrelated text and binary content;
 *  - archive member-tree fingerprint recognises the SAME files repacked with different compression
 *    (different bytes → different content hash) and rejects a different member set / non-ZIP file.
 */
class StructuralFingerprintSourceTest {

    private val source = StructuralFingerprintSource()

    private fun tempDir() = java.nio.file.Files.createTempDirectory("jupiter-struct").toFile()

    @Test
    fun textSimHash_reformattedCopyIsNear_unrelatedIsFar() {
        val dir = tempDir()
        try {
            val original = File(dir, "Main.kt").apply {
                writeText("fun main() {\n    val x = 1\n    println(x)\n}\n")
            }
            // Same code, re-indented and re-spaced (a content hash would shatter; SimHash survives).
            val reformatted = File(dir, "Main2.kt").apply {
                writeText("fun main(){\n        val x=1\n        println(x)\n}\n")
            }
            val unrelated = File(dir, "Other.kt").apply {
                writeText("class Repository(val dao: Dao) { suspend fun load() = dao.everything() }")
            }

            val h1 = source.textSimHash(original.absolutePath)
            val h2 = source.textSimHash(reformatted.absolutePath)
            val h3 = source.textSimHash(unrelated.absolutePath)

            assertNotNull(h1); assertNotNull(h2); assertNotNull(h3)
            assertTrue(
                "reformatted copy must be near (distance=${TextSimHash.distance(h1!!, h2!!)})",
                TextSimHash.isNear(h1, h2, StructuralHash.TEXT_NEAR_THRESHOLD),
            )
            assertFalse(
                "unrelated code must not be near",
                TextSimHash.isNear(h1, h3!!, StructuralHash.TEXT_NEAR_THRESHOLD),
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun textSimHash_binaryContentIsEmpty() {
        val dir = tempDir()
        try {
            val binary = File(dir, "blob.kt").apply { writeBytes(byteArrayOf(1, 0, 2, 0, 3)) }
            assertEquals(TextSimHash.EMPTY, source.textSimHash(binary.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeZip(file: File, level: Int, members: List<Pair<String, String>>) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.setLevel(level)
            for ((name, content) in members) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun archiveTreeHash_sameMembersDifferentCompressionAreEqual() {
        val dir = tempDir()
        try {
            val members = listOf("a.txt" to "hello world", "sub/b.txt" to "second file body")
            val stored = File(dir, "stored.zip").apply { writeZip(this, 0, members) }      // no compression
            val deflated = File(dir, "deflated.zip").apply { writeZip(this, 9, members) }   // max compression

            // Different bytes on disk (different compression), same contents.
            assertNotEquals(stored.length(), deflated.length())

            val h1 = source.archiveTreeHash(stored.absolutePath)
            val h2 = source.archiveTreeHash(deflated.absolutePath)

            assertNotNull(h1)
            assertNotEquals(StructuralHash.UNHASHABLE, h1)
            assertEquals("same member (name,size,crc) set → same fingerprint", h1, h2)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun archiveTreeHash_differentMembersDiffer_nonZipIsUnhashable() {
        val dir = tempDir()
        try {
            val a = File(dir, "a.zip").apply { writeZip(this, 6, listOf("x.txt" to "one")) }
            val b = File(dir, "b.zip").apply { writeZip(this, 6, listOf("x.txt" to "TWO different")) }
            assertNotEquals(source.archiveTreeHash(a.absolutePath), source.archiveTreeHash(b.absolutePath))

            val notZip = File(dir, "c.zip").apply { writeText("I am not a zip archive") }
            assertEquals(StructuralHash.UNHASHABLE, source.archiveTreeHash(notZip.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFileYieldsNull() {
        assertNull(source.textSimHash("/does/not/exist.kt"))
        assertNull(source.archiveTreeHash("/does/not/exist.zip"))
    }
}
