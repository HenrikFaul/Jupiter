package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure unit tests for the app-storage model math (JVM, no Android). */
class AppStorageInfoTest {

    private fun app(pkg: String, appB: Long, dataB: Long, cacheB: Long) =
        AppStorageInfo(pkg, pkg, appB, dataB, cacheB, isSystemApp = false)

    /**
     * Platform semantics: `StorageStats.getDataBytes` already INCLUDES the cache, so the
     * total is code + data — NOT code + data + cache (which double-counted cache in v0.29).
     */
    @Test
    fun `totalBytes is code plus data, cache not double-counted`() {
        assertEquals(30L, app("a", 10L, 20L, 5L).totalBytes)
        assertEquals(0L, app("b", 0L, 0L, 0L).totalBytes)
    }

    /** The "Data" figure shown to users excludes the clearable cache subset. */
    @Test
    fun `dataBytesExcludingCache subtracts cache and never goes negative`() {
        assertEquals(15L, app("a", 10L, 20L, 5L).dataBytesExcludingCache)
        // Defensive: a platform quirk reporting cache > data must not yield a negative size.
        assertEquals(0L, app("weird", 10L, 3L, 9L).dataBytesExcludingCache)
    }

    /** The three displayed parts (app · data · cache) must sum back to the total. */
    @Test
    fun `displayed parts sum to totalBytes`() {
        val a = app("a", 10L, 20L, 5L)
        assertEquals(a.totalBytes, a.appBytes + a.dataBytesExcludingCache + a.cacheBytes)
    }

    @Test
    fun `apps sort by total size descending`() {
        val apps = listOf(
            app("small", 1L, 1L, 1L),      // 2
            app("big", 100L, 100L, 50L),   // 200
            app("mid", 10L, 10L, 5L),      // 20
        ).sortedByDescending { it.totalBytes }
        assertEquals(listOf("big", "mid", "small"), apps.map { it.packageName })
    }
}
