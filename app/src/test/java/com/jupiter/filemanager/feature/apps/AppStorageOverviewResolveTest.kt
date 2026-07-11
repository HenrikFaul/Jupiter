package com.jupiter.filemanager.feature.apps

import com.jupiter.filemanager.domain.model.AppStorageInfo
import com.jupiter.filemanager.domain.model.AppStorageOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Pure proof of the streaming-scan display rule ([resolveOverview]) that a pre-merge adversarial
 * review flagged: when the App-storage screen is RELOADED (Refresh button / screen resume) while it
 * already shows a full list, the incoming partial "scanning" frames must NOT downsize the visible
 * list to the first 5-app batch and regrow it over the multi-second scan. The full list stays until
 * the fresh scan COMPLETES; a first-ever scan (no apps yet) still streams its partials in.
 */
class AppStorageOverviewResolveTest {

    private fun app(pkg: String, total: Long) =
        AppStorageInfo(pkg, pkg, appBytes = total, dataBytes = 0L, cacheBytes = 0L, isSystemApp = false)

    private fun overview(count: Int, scanning: Boolean, permissionRequired: Boolean = false) =
        AppStorageOverview(
            apps = (1..count).map { app("p$it", it.toLong()) },
            totalBytes = 0L,
            cacheBytes = 0L,
            permissionRequired = permissionRequired,
            scanning = scanning,
        )

    private val full = overview(count = 120, scanning = false)

    @Test
    fun firstEverScanAdoptsEveryPartial() {
        // No existing apps → the empty scanning frame and each partial are shown so the list fills.
        val empty = overview(count = 0, scanning = true)
        val firstPartial = overview(count = 5, scanning = true)
        assertSame(empty, resolveOverview(current = null, incoming = empty))
        assertSame(firstPartial, resolveOverview(current = empty, incoming = firstPartial))
    }

    @Test
    fun reloadKeepsFullListThroughEmptyThenPartialFrames() {
        // Refresh/resume: existing 120-app list must survive the empty frame AND the 5-app partial.
        val emptyFrame = overview(count = 0, scanning = true)
        val fivePartial = overview(count = 5, scanning = true)
        assertSame(full, resolveOverview(current = full, incoming = emptyFrame))
        assertSame(full, resolveOverview(current = full, incoming = fivePartial))
        // A larger mid-scan partial is still withheld — no visible shrink/regrow at all.
        val thirtyPartial = overview(count = 30, scanning = true)
        assertSame(full, resolveOverview(current = full, incoming = thirtyPartial))
    }

    @Test
    fun reloadAdoptsTheCompletedScanAtomically() {
        val freshComplete = overview(count = 118, scanning = false)
        assertSame(freshComplete, resolveOverview(current = full, incoming = freshComplete))
        assertEquals(118, resolveOverview(current = full, incoming = freshComplete).apps.size)
    }

    @Test
    fun permissionFrameIsAlwaysAdoptedEvenOverAFullList() {
        // Access revoked between reloads: the authoritative permission frame must replace the list.
        val permission = AppStorageOverview(permissionRequired = true)
        assertSame(permission, resolveOverview(current = full, incoming = permission))
    }
}
