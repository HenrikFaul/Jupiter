package com.jupiter.filemanager.data.index

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactMetadataCodecTest {

    @Test
    fun `sha1 round trip halves persistent payload`() {
        val hex = "0123456789abcdef0123456789abcdef01234567"
        val blob = CompactMetadataCodec.sha1ToBytes(hex)

        assertEquals(20, blob!!.size)
        assertEquals(hex, CompactMetadataCodec.bytesToHex(blob))
        assertTrue(blob.size * 2 == hex.toByteArray(Charsets.UTF_8).size)
        assertNull(CompactMetadataCodec.sha1ToBytes("not-a-production-digest"))
    }

    @Test
    fun `ordered signed long vector round trips without text expansion`() {
        val values = listOf(Long.MIN_VALUE, -1L, 0L, 0x1234_5678_9ABC_DEF0L, Long.MAX_VALUE)
        val blob = CompactMetadataCodec.encodeLongVector(values)
        val legacy = MediaFingerprint(values).encode()

        assertEquals(values, CompactMetadataCodec.decodeLongVector(blob))
        assertArrayEquals(blob, CompactMetadataCodec.legacyLongVectorToBlob(legacy))
        assertEquals(40, blob.size)
        assertEquals(84, legacy.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `packed geometry is orientation independent and rejects unrelated shapes`() {
        val fullHd = CompactMetadataCodec.packDimensions(1920, 1080)
        val resized = CompactMetadataCodec.packDimensions(1280, 720)
        val rotated = CompactMetadataCodec.packDimensions(1080, 1920)
        val square = CompactMetadataCodec.packDimensions(1080, 1080)

        assertEquals(1920 to 1080, CompactMetadataCodec.unpackDimensions(fullHd))
        assertTrue(CompactMetadataCodec.dimensionsCompatible(fullHd, resized))
        assertTrue(CompactMetadataCodec.dimensionsCompatible(fullHd, rotated))
        assertFalse(CompactMetadataCodec.dimensionsCompatible(fullHd, square))
        assertTrue(CompactMetadataCodec.dimensionsCompatible(fullHd, null))
    }

    @Test
    fun `malformed legacy metadata fails closed instead of manufacturing evidence`() {
        assertNull(CompactMetadataCodec.hexToBytes("abc"))
        assertNull(CompactMetadataCodec.legacyQuickHashToBytes("12:not-hex"))
        assertNull(CompactMetadataCodec.legacyLongVectorToBlob("1234,bad"))
        assertNull(CompactMetadataCodec.decodeLongVector(ByteArray(7)))
    }
}
