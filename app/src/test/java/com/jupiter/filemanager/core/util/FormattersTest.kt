package com.jupiter.filemanager.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormattersTest {

    @Before
    fun setUp() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun formatBytes_zero_returnsZeroB() {
        assertEquals("0 B", formatBytes(0L))
    }

    @Test
    fun formatBytes_negative_returnsZeroB() {
        assertEquals("0 B", formatBytes(-1L))
        assertEquals("0 B", formatBytes(-1024L))
    }

    @Test
    fun formatBytes_smallWholeBytes_noDecimalPoint() {
        // digitGroups == 0 -> rendered as raw byte count with " B" suffix.
        assertEquals("512 B", formatBytes(512L))
    }

    @Test
    fun formatBytes_kilobytes_hasKbSuffixAndOneDecimal() {
        val result = formatBytes(1024L)
        assertTrue("expected KB suffix, got: $result", result.endsWith(" KB"))
        // 1024 / 1024 = 1.0
        assertEquals("1.0 KB", result)
    }

    @Test
    fun formatBytes_kilobytes_plausibleNumericPart() {
        val result = formatBytes(1536L) // 1.5 KB
        assertTrue("expected KB suffix, got: $result", result.endsWith(" KB"))
        assertEquals("1.5 KB", result)
    }

    @Test
    fun formatBytes_megabytes_hasMbSuffixAndPlausibleValue() {
        val result = formatBytes(5L * 1024L * 1024L) // exactly 5 MB
        assertTrue("expected MB suffix, got: $result", result.endsWith(" MB"))
        assertEquals("5.0 MB", result)
    }

    @Test
    fun formatBytes_gigabytes_hasGbSuffixAndPlausibleValue() {
        val result = formatBytes(1_500_000_000L) // ~1.4 GB per impl doc
        assertTrue("expected GB suffix, got: $result", result.endsWith(" GB"))
        // 1_500_000_000 / 1024^3 ~= 1.3969...
        val numeric = result.removeSuffix(" GB").toDouble()
        assertTrue("numeric part should be > 1 and < 2, got: $numeric", numeric in 1.0..2.0)
    }

    @Test
    fun formatBytes_gigabytes_exactValue() {
        val result = formatBytes(2L * 1024L * 1024L * 1024L) // exactly 2 GB
        assertEquals("2.0 GB", result)
    }

    @Test
    fun formatItemCount_one_isSingular() {
        assertEquals("1 item", formatItemCount(1))
    }

    @Test
    fun formatItemCount_five_isPlural() {
        assertEquals("5 items", formatItemCount(5))
    }

    @Test
    fun formatItemCount_zero_isPlural() {
        assertEquals("0 items", formatItemCount(0))
    }
}
