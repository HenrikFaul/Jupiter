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
 * Backed by [EncryptedSharedPreferences] (AES256_GCM via a [MasterKey]). In the rare
 * case the Android KeyStore or the underlying crypto/IO layer throws, we fall back to a
 * plain [SharedPreferences] instance so the app never crashes. Passwords are stored under
 * keys prefixed with [KEY_PREFIX].
 */
@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (t: Throwable) {
            // KeyStore corruption / IO / GeneralSecurityException — never crash the app.
            context.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun keyFor(connectionId: String): String = KEY_PREFIX + connectionId

    fun savePassword(connectionId: String, password: String?) {
        try {
            val editor = prefs.edit()
            if (password == null) {
                editor.remove(keyFor(connectionId))
            } else {
                editor.putString(keyFor(connectionId), password)
            }
            editor.apply()
        } catch (t: Throwable) {
            // Swallow — persistence failures must not crash the caller.
        }
    }

    fun getPassword(connectionId: String): String? {
        return try {
            prefs.getString(keyFor(connectionId), null)
        } catch (t: Throwable) {
            null
        }
    }

    fun deletePassword(connectionId: String) {
        try {
            prefs.edit().remove(keyFor(connectionId)).apply()
        } catch (t: Throwable) {
            // Ignore.
        }
    }

    private fun secretKeyFor(key: String): String = SECRET_PREFIX + key

    /**
     * Persists an arbitrary secret [value] under [key] in the same
     * [EncryptedSharedPreferences] store used for passwords. A null [value]
     * removes the entry. Crash-safe: persistence failures are swallowed so the
     * caller is never crashed by a KeyStore/IO error.
     */
    fun saveSecret(key: String, value: String?) {
        try {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(secretKeyFor(key))
            } else {
                editor.putString(secretKeyFor(key), value)
            }
            editor.apply()
        } catch (t: Throwable) {
            // Swallow — persistence failures must not crash the caller.
        }
    }

    /**
     * Reads a secret previously stored via [saveSecret], or null if absent or on
     * any KeyStore/crypto/IO failure.
     */
    fun getSecret(key: String): String? {
        return try {
            prefs.getString(secretKeyFor(key), null)
        } catch (t: Throwable) {
            null
        }
    }

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "jupiter_remote_credentials"
        const val FALLBACK_PREFS_NAME = "jupiter_remote_credentials_fallback"
        const val KEY_PREFIX = "pwd_"
        const val SECRET_PREFIX = "secret_"
    }
}
