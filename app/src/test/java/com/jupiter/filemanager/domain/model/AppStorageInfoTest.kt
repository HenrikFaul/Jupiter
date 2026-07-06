package com.jupiter.filemanager.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure unit tests for the app-storage model math (JVM, no Android). */
class AppStorageInfoTest {

    private fun app(pkg: String, appB: Long, dataB: Long, cacheB: Long) =
        AppStorageInfo(pkg, pkg, appB, dataB, cacheB, isSystemApp = false)

    @Test
    fun `totalBytes sums code data and cache`() {
        assertEquals(60L, app("a", 10L, 20L, 30L).totalBytes)
        assertEquals(0L, app("b", 0L, 0L, 0L).totalBytes)
    }

    @Test
    fun `apps sort by total size descending`() {
        val apps = listOf(
            app("small", 1L, 1L, 1L),      // 3
            app("big", 100L, 100L, 100L),  // 300
            app("mid", 10L, 10L, 10L),     // 30
        ).sortedByDescending { it.totalBytes }
        assertEquals(listOf("big", "mid", "small"), apps.map { it.packageName })
    }
}
