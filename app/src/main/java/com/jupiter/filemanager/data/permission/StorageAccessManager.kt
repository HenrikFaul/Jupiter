package com.jupiter.filemanager.data.permission

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes the level of storage access the app currently has.
 *
 * - [FULL_ACCESS]: full file-system access (MANAGE_EXTERNAL_STORAGE on R+, or
 *   READ/WRITE external storage granted on pre-R devices).
 * - [SCOPED_ONLY]: the app can only reach scoped storage (e.g. its own app-specific
 *   directories and MediaStore); no broad file access.
 * - [NONE]: no usable storage permission has been granted.
 */
enum class StorageAccessState { FULL_ACCESS, SCOPED_ONLY, NONE }

/**
 * Minimal seam over [StorageAccessManager.hasAllFilesAccess] so background components (e.g. the
 * dedup reconciler) can gate on broad storage access and still be unit-tested with a simple fake.
 */
interface StorageAccessGate {
    fun hasFullAccess(): Boolean
}

/**
 * Centralizes detection and request of storage permissions across Android versions.
 *
 * On Android R (API 30) and above we rely on "All files access"
 * (MANAGE_EXTERNAL_STORAGE) reported via [Environment.isExternalStorageManager].
 * On older devices we fall back to the legacy READ/WRITE external-storage runtime
 * permissions.
 */
@Singleton
class StorageAccessManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : StorageAccessGate {

    /** [StorageAccessGate] implementation — full access == All-Files-Access (R+) / legacy grant. */
    override fun hasFullAccess(): Boolean = hasAllFilesAccess()

    /**
     * Legacy runtime permissions used on pre-R devices. Empty on R+ where
     * MANAGE_EXTERNAL_STORAGE governs broad access instead.
     */
    val legacyPermissions: List<String> =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            listOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        } else {
            emptyList()
        }

    /**
     * Resolves the current overall storage-access state.
     */
    fun currentState(): StorageAccessState {
        return when {
            hasAllFilesAccess() -> StorageAccessState.FULL_ACCESS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> StorageAccessState.SCOPED_ONLY
            else -> StorageAccessState.NONE
        }
    }

    /**
     * True when the app has full file-system access.
     *
     * On R+ this maps to [Environment.isExternalStorageManager]; below R it maps to
     * the legacy READ/WRITE external-storage runtime permissions being granted.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            legacyReadWriteGranted()
        }
    }

    /**
     * True when both legacy READ and WRITE external-storage permissions are granted.
     * Always false above R where these permissions no longer grant broad access.
     */
    fun legacyReadWriteGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        return legacyPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Builds an [Intent] that leads the user to the "All files access" settings screen
     * for this app on R+. Prefers the app-scoped settings action and falls back to the
     * generic list of apps with all-files access. On pre-R devices a generic app-details
     * settings intent is returned.
     */
    fun manageAllFilesSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        val packageUri = Uri.fromParts("package", context.packageName, null)
        val appSpecific = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            packageUri,
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // If the app-specific screen is resolvable, prefer it; otherwise fall back to
        // the global all-files-access list.
        val resolved = appSpecific.resolveActivity(context.packageManager) != null
        return if (resolved) {
            appSpecific
        } else {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }
}
