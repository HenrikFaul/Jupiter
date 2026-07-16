package com.jupiter.filemanager.data.index

import com.jupiter.filemanager.domain.model.FileType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFingerprintMatcherTest {

    private val base = 0x1234_5678_9ABC_DEF0L

    private fun video(hashes: List<Long>, duration: Long = 30_000L) =
        MediaFingerprint(hashes, extent = duration)

    @Test
    fun `video requires agreement across the timeline not one shared frame`() {
        val original = video(List(5) { base })
        val reencoded = video(List(5) { base xor 0b11L })
        assertTrue(MediaFingerprintMatcher.matches(FileType.VIDEO, original, reencoded))

        val unrelatedWithSharedMiddle = video(
            listOf(base.inv(), base.inv(), base, base.inv(), base.inv()),
        )
        assertFalse(
            "one shared thumbnail must never prove same footage",
            MediaFingerprintMatcher.matches(FileType.VIDEO, original, unrelatedWithSharedMiddle),
        )
    }

    @Test
    fun `duration mismatch and low-information frames veto video similarity`() {
        val a = video(List(5) { base }, duration = 30_000L)
        val differentDuration = video(List(5) { base }, duration = 60_000L)
        assertFalse(MediaFingerprintMatcher.matches(FileType.VIDEO, a, differentDuration))

        val blackFrames = video(List(5) { 0L }, duration = 30_000L)
        assertFalse(MediaFingerprintMatcher.matches(FileType.VIDEO, blackFrames, blackFrames))
    }

    @Test
    fun `video geometry rejects different shapes but accepts resize and rotation`() {
        val landscape = MediaFingerprint(
            List(5) { base }, extent = 30_000L, width = 1920, height = 1080,
        )
        val resized = MediaFingerprint(
            List(5) { base }, extent = 30_000L, width = 1280, height = 720,
        )
        val rotated = MediaFingerprint(
            List(5) { base }, extent = 30_000L, width = 1080, height = 1920,
        )
        val square = MediaFingerprint(
            List(5) { base }, extent = 30_000L, width = 1080, height = 1080,
        )

        assertTrue(MediaFingerprintMatcher.matches(FileType.VIDEO, landscape, resized))
        assertTrue(MediaFingerprintMatcher.matches(FileType.VIDEO, landscape, rotated))
        assertFalse(MediaFingerprintMatcher.matches(FileType.VIDEO, landscape, square))
    }

    @Test
    fun `pdf page count and audio duration are hard type gates`() {
        val pdf3 = MediaFingerprint(List(3) { base }, extent = 3L)
        val pdf4 = MediaFingerprint(List(3) { base }, extent = 4L)
        assertFalse(MediaFingerprintMatcher.matches(FileType.PDF, pdf3, pdf4))

        val audio = MediaFingerprint(listOf(base), extent = 180_000L)
        val audioReencode = MediaFingerprint(listOf(base xor 0b11L), extent = 180_500L)
        val differentTrack = MediaFingerprint(listOf(base), extent = 240_000L)
        assertTrue(MediaFingerprintMatcher.matches(FileType.AUDIO, audio, audioReencode))
        assertFalse(MediaFingerprintMatcher.matches(FileType.AUDIO, audio, differentTrack))
    }

    @Test
    fun `legacy single-frame descriptor fails closed`() {
        assertFalse(
            MediaFingerprintMatcher.matches(
                FileType.VIDEO,
                MediaFingerprint.single(base),
                MediaFingerprint.single(base),
            ),
        )
    }
}
