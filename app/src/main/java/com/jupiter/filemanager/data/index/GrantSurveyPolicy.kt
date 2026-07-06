package com.jupiter.filemanager.data.index

/**
 * Pure policy deciding whether confirming/granting full storage access should kick off a
 * fresh full-storage survey right NOW.
 *
 * This exists so "give access → the scan actually starts" is a single, unit-tested decision
 * rather than an implicit side effect. The first-run survey is scheduled at process start
 * ([com.jupiter.filemanager.JupiterApp]), which happens BEFORE the user has granted All Files
 * Access — so that survey no-ops (the worker cannot see the shared volume) and the index stays
 * [IndexStatus.EMPTY]. Nothing else re-triggered a scan once access was finally granted, so the
 * user previously had to cold-restart the app for their grant to take effect. The permission
 * screen now calls [shouldStartSurvey] on every access re-check and, when true, asks the
 * scheduler to run the survey.
 */
object GrantSurveyPolicy {

    /**
     * @param hasFullAccess whether the app currently holds full (All Files) storage access.
     * @param indexingEnabled the user's "keep a file index" preference (respected, so a grant
     *   never forces a scan the user opted out of).
     * @param status the index's current life-cycle [IndexStatus].
     * @return true only when we have access, indexing is enabled, and the index is neither
     *   already [IndexStatus.COMPLETE] nor a survey already [IndexStatus.RUNNING] — so a grant
     *   never restarts an in-flight scan nor redundantly rescans a finished one, but DOES start
     *   one from EMPTY / DIRTY / FAILED.
     */
    fun shouldStartSurvey(
        hasFullAccess: Boolean,
        indexingEnabled: Boolean,
        status: IndexStatus,
    ): Boolean =
        hasFullAccess &&
            indexingEnabled &&
            status != IndexStatus.RUNNING &&
            status != IndexStatus.COMPLETE
}
