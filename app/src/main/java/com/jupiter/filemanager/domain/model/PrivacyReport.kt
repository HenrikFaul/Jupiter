package com.jupiter.filemanager.domain.model

/**
 * Overall privacy posture derived from real on-device signals.
 */
enum class PrivacyLevel { GOOD, FAIR, AT_RISK }

/**
 * Snapshot of the device's file-privacy posture.
 *
 * Counts are derived from real repositories where available (e.g. vault size);
 * unavailable signals default to 0 rather than fabricated values.
 */
data class PrivacyReport(
    val level: PrivacyLevel,
    val encryptedFiles: Int,
    val hiddenFiles: Int,
    val sharedLinks: Int,
    val appsWithAccess: Int,
    val dataExposure: String,
)
