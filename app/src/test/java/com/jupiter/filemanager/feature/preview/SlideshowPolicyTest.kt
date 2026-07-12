package com.jupiter.filemanager.feature.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowPolicyTest {

    @Test
    fun `automatic playback uses the required three second cadence`() {
        assertEquals(3_000L, SlideshowPolicy.FRAME_INTERVAL_MILLIS)
        assertTrue(SlideshowPolicy.canAutoAdvance(isPlaying = true, itemCount = 2))
        assertFalse(SlideshowPolicy.canAutoAdvance(isPlaying = false, itemCount = 2))
        assertFalse(SlideshowPolicy.canAutoAdvance(isPlaying = true, itemCount = 1))
    }

    @Test
    fun `manual and automatic navigation wrap without invalid indexes`() {
        assertEquals(1, SlideshowPolicy.nextIndex(currentIndex = 0, itemCount = 3))
        assertEquals(0, SlideshowPolicy.nextIndex(currentIndex = 2, itemCount = 3))
        assertEquals(2, SlideshowPolicy.previousIndex(currentIndex = 0, itemCount = 3))
        assertEquals(1, SlideshowPolicy.previousIndex(currentIndex = 2, itemCount = 3))
        assertEquals(0, SlideshowPolicy.nextIndex(currentIndex = 9, itemCount = 0))
        assertEquals(0, SlideshowPolicy.previousIndex(currentIndex = -1, itemCount = 0))
    }
}
