package com.jupiter.filemanager.data.index.dedup

/**
 * Identity descriptor for an APK — the STRUCTURAL-layer signal for app packages. For APKs the
 * strongest signals are NOT bytes or visuals but the package name, the signing certificate, and
 * the version code: two APKs of the same app across versions share package + signer but differ in
 * versionCode (a "versioned family", NOT a duplicate to auto-remove), whereas a matching package
 * name with a DIFFERENT signer is a red flag (repackaged/impersonating app) and must never be
 * treated as "the same app".
 *
 * @property packageName application id.
 * @property versionCode monotonically increasing version (longVersionCode).
 * @property signerSha hex SHA-256 of the signing certificate, or null when unsigned/unreadable.
 */
data class ApkIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerSha: String?,
)

/** How two APKs relate. */
enum class ApkRelation {
    /** Same package, same signer, same version — a genuine duplicate. */
    SAME_EXACT,

    /** Same package + signer, different version — an update of the same app (a family, not a dup). */
    SAME_APP_UPDATE,

    /** Same package name but DIFFERENT signer — suspicious (repackaged); never "the same app". */
    DIFFERENT_SIGNER,

    /** Different package — unrelated apps. */
    UNRELATED,
}

object ApkComparator {
    fun relationOf(a: ApkIdentity, b: ApkIdentity): ApkRelation {
        if (a.packageName != b.packageName) return ApkRelation.UNRELATED
        // Same package name from here on.
        if (a.signerSha == null || b.signerSha == null || a.signerSha != b.signerSha) {
            return ApkRelation.DIFFERENT_SIGNER
        }
        return if (a.versionCode == b.versionCode) ApkRelation.SAME_EXACT else ApkRelation.SAME_APP_UPDATE
    }

    /**
     * Maps the APK relation onto the fusion engine: a structural similarity in [0,1] plus any
     * hard veto. A different signer vetoes (caps the score) even though the package matches.
     */
    fun toSignal(relation: ApkRelation): Pair<Double, Set<Veto>> = when (relation) {
        ApkRelation.SAME_EXACT -> 1.0 to emptySet()
        ApkRelation.SAME_APP_UPDATE -> 0.85 to emptySet() // strong "same app", not exact
        ApkRelation.DIFFERENT_SIGNER -> 0.3 to setOf(Veto.SIGNER_MISMATCH)
        ApkRelation.UNRELATED -> 0.0 to emptySet()
    }
}
