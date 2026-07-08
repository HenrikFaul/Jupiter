package com.jupiter.filemanager.feature.cleanup

import com.jupiter.filemanager.domain.model.DuplicateGroup
import com.jupiter.filemanager.domain.model.MediaQuality
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-lifetime cache of the last completed duplicate analysis, so re-opening the Duplicates
 * screen renders the previous result INSTANTLY instead of showing a blank scanning screen for many
 * seconds every time. The `@HiltViewModel` is recreated on each navigation to the screen, so an
 * in-ViewModel field would not survive; this `@Singleton` does.
 *
 * The cached result is a starting point, not the final word: the ViewModel still kicks a **silent**
 * background rescan on open that swaps in fresh results when it finishes, so the list is never stale.
 * (A full process death clears this cache — the next cold start rescans once and re-populates it.)
 */
@Singleton
class DuplicateScanCache @Inject constructor() {

    @Volatile
    private var cachedGroups: List<DuplicateGroup>? = null

    @Volatile
    private var cachedQualities: Map<String, MediaQuality> = emptyMap()

    /** The last completed scan's groups, or null when no scan has completed this process. */
    val groups: List<DuplicateGroup>? get() = cachedGroups

    /** Probed media-quality labels from the last scan (may be partial). */
    val qualities: Map<String, MediaQuality> get() = cachedQualities

    /** Records a completed scan's groups (an empty list is valid — "scanned, no duplicates"). */
    fun saveGroups(groups: List<DuplicateGroup>) {
        cachedGroups = groups
    }

    /** Records the latest probed qualities (merged view the UI is showing). */
    fun saveQualities(qualities: Map<String, MediaQuality>) {
        cachedQualities = qualities
    }
}
