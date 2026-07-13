package com.jupiter.filemanager.data.index

/**
 * Explicit outcome contract for one historical-image descriptor attempt.
 *
 * `computeAll == null` is deliberately different from [PerceptualFingerprint.UNHASHABLE]: null
 * means the OS/provider/decoder did not give us a trustworthy answer *this time*, so hiding the
 * image from future Similar-photo passes would be data loss. The worker retries it instead.
 */
internal sealed interface PerceptualBackfillDecision {
    data class Persist(val fingerprint: PerceptualFingerprint) : PerceptualBackfillDecision
    data object Retry : PerceptualBackfillDecision
}

internal fun perceptualBackfillDecision(
    fingerprint: PerceptualFingerprint?,
): PerceptualBackfillDecision = when (fingerprint) {
    null -> PerceptualBackfillDecision.Retry
    else -> PerceptualBackfillDecision.Persist(fingerprint)
}

/**
 * A user who opens Duplicate cleanup has explicitly requested image comparison. This is allowed
 * even when the optional continuous background index is disabled; it is not permission for a
 * hidden periodic scan, merely the user-visible tool completing the work they asked it to do.
 */
internal fun mayRunPerceptualBackfill(
    indexingEnabled: Boolean,
    explicitUserRequest: Boolean,
): Boolean = indexingEnabled || explicitUserRequest
