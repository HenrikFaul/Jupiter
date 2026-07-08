package com.jupiter.filemanager.feature.privacy

import com.jupiter.filemanager.domain.model.PrivacyReport

/**
 * UI state for the Privacy Dashboard.
 *
 * The [report] is derived from real on-device signals (vault contents, settings,
 * and a best-effort scan of the primary storage root). Signals that require a
 * backend not yet implemented (shared links, third-party app access) honestly
 * report zero rather than fabricating data.
 */
data class PrivacyDashboardUiState(
    val isLoading: Boolean = true,
    val report: PrivacyReport? = null,
    val errorMessage: String? = null,
)
