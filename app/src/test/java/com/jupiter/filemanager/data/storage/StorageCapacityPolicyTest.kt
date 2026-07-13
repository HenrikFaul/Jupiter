package com.jupiter.filemanager.data.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageCapacityPolicyTest {

    @Test
    fun dataPartitionOf256GbDeviceRoundsToAdvertisedCapacity() {
        assertEquals(
            256_000_000_000L,
            roundToAdvertisedStorageSize(238_400_000_000L),
        )
    }

    @Test
    fun platformCapacityWinsWhenItReportsWholeDevice() {
        assertEquals(
            256_000_000_000L,
            selectPrimaryCapacity(
                fileSystemTotalBytes = 222_100_000_000L,
                platformTotalBytes = 256_000_000_000L,
            ),
        )
    }

    @Test
    fun frameworkStyleRoundingRecoversCapacityWhenPlatformApiIsUnavailable() {
        assertEquals(
            256_000_000_000L,
            selectPrimaryCapacity(
                fileSystemTotalBytes = 238_400_000_000L,
                platformTotalBytes = 0L,
            ),
        )
    }

    @Test
    fun successfulPlatformValueIsNotRebucketed() {
        assertEquals(
            240_000_000_000L,
            selectPrimaryCapacity(
                fileSystemTotalBytes = 238_400_000_000L,
                platformTotalBytes = 240_000_000_000L,
            ),
        )
    }

    @Test
    fun selectedCapacityNeverShrinksBelowReadableFileSystem() {
        assertEquals(
            256_000_000_000L,
            selectPrimaryCapacity(
                fileSystemTotalBytes = 256_000_000_000L,
                platformTotalBytes = 128_000_000_000L,
            ),
        )
    }

    @Test
    fun platformFreeBytesMatchAndroidStorageSurface() {
        assertEquals(
            26_500_000_000L,
            selectPrimaryAvailableBytes(
                fileSystemAvailableBytes = 24_700_000_000L,
                platformTotalBytes = 256_000_000_000L,
                platformAvailableBytes = 26_500_000_000L,
                selectedTotalBytes = 256_000_000_000L,
            ),
        )
    }

    @Test
    fun invalidPlatformFreeBytesFallBackToFileSystem() {
        assertEquals(
            24_700_000_000L,
            selectPrimaryAvailableBytes(
                fileSystemAvailableBytes = 24_700_000_000L,
                platformTotalBytes = 256_000_000_000L,
                platformAvailableBytes = 300_000_000_000L,
                selectedTotalBytes = 256_000_000_000L,
            ),
        )
    }

    @Test
    fun zeroAndInvalidInputsStaySafe() {
        assertEquals(0L, roundToAdvertisedStorageSize(0L))
        assertEquals(-1L, roundToAdvertisedStorageSize(-1L))
        assertEquals(0L, selectPrimaryCapacity(0L, 256_000_000_000L))
    }
}
