package com.jupiter.filemanager.data.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores remote-connection passwords at rest, keyed by connection id.
 *
 * Backed by [EncryptedSharedPreferences] (AES256_GCM via a [MasterKey]). Android KeyStore or
 * encrypted-preference failures are deliberately **fail closed**: callers get a failed write or
 * a null read and must ask the user to authenticate again. A secret must never survive by being
 * written to ordinary [SharedPreferences]. Passwords are stored under keys prefixed with
 * [KEY_PREFIX].
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * The Result is cached so a broken KeyStore is handled consistently for the process lifetime.
     * Retrying a failure by silently choosing another persistence backend would be a security
     * downgrade, not a recovery strategy.
     */
    private val encryptedPrefs: Result<SharedPreferences> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { createEncryptedPrefs() }
    }

    private fun createEncryptedPrefs(): SharedPreferences {
        // A pre-v0.57 build could create this plaintext fallback file. It cannot be migrated
        // safely while the Keystore is unavailable, so remove it before opening the encrypted
        // store. This may require a remote re-authentication but never leaves a recoverable
        // password/token on disk.
        context.getSharedPreferences(LEGACY_FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun keyFor(connectionId: String): String = KEY_PREFIX + connectionId

    fun savePassword(connectionId: String, password: String?): Boolean =
        write(keyFor(connectionId), password)

    fun getPassword(connectionId: String): String? {
        val prefs = encryptedPrefs.getOrNull() ?: return null
        return runCatching { prefs.getString(keyFor(connectionId), null) }.getOrNull()
    }

    fun deletePassword(connectionId: String): Boolean = write(keyFor(connectionId), null)

    private fun secretKeyFor(key: String): String = SECRET_PREFIX + key

    /**
     * Persists an arbitrary secret [value] under [key] in the same
     * [EncryptedSharedPreferences] store used for passwords. A null [value]
     * removes the entry. The Boolean result lets a caller keep the UI responsive
     * while refusing to claim that a secret was persisted after a KeyStore/IO error.
     */
    fun saveSecret(key: String, value: String?): Boolean = write(secretKeyFor(key), value)

    /**
     * Reads a secret previously stored via [saveSecret], or null if absent or on
     * any KeyStore/crypto/IO failure.
     */
    fun getSecret(key: String): String? {
        val prefs = encryptedPrefs.getOrNull() ?: return null
        return runCatching { prefs.getString(secretKeyFor(key), null) }.getOrNull()
    }

    private fun write(key: String, value: String?): Boolean {
        val prefs = encryptedPrefs.getOrNull() ?: return false
        return runCatching {
            val editor = prefs.edit()
            if (value == null) editor.remove(key) else editor.putString(key, value)
            // `commit` gives the caller a truthful, synchronous persistence outcome. Remote
            // definitions are only published after this succeeds.
            editor.commit()
        }.getOrDefault(false)
    }

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "jupiter_remote_credentials"
        const val LEGACY_FALLBACK_PREFS_NAME = "jupiter_remote_credentials_fallback"
        const val KEY_PREFIX = "pwd_"
        const val SECRET_PREFIX = "secret_"
    }
}
