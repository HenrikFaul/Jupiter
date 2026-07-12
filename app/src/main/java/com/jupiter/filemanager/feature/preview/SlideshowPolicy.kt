package com.jupiter.filemanager.feature.preview

/**
 * Pure playback rules shared by the image-gallery UI and its unit tests.
 *
 * A slideshow advances only while explicitly playing and when at least two images
 * exist. Navigation wraps in both directions so playback can continue without
 * manufacturing an invalid page index.
 */
object SlideshowPolicy {
    const val FRAME_INTERVAL_MILLIS: Long = 3_000L

    fun canAutoAdvance(isPlaying: Boolean, itemCount: Int): Boolean =
        isPlaying && itemCount > 1

    fun nextIndex(currentIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val safeCurrent = currentIndex.coerceIn(0, itemCount - 1)
        return (safeCurrent + 1) % itemCount
    }

    fun previousIndex(currentIndex: Int, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val safeCurrent = currentIndex.coerceIn(0, itemCount - 1)
        return if (safeCurrent == 0) itemCount - 1 else safeCurrent - 1
    }
}
