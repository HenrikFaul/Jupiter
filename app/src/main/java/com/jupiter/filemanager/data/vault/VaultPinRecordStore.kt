package com.jupiter.filemanager.data.vault

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/** Distinguishes a genuinely absent PIN record from a storage/KeyStore failure. */
sealed interface VaultPinRecordReadResult {
    data object Missing : VaultPinRecordReadResult
    data class Present(val record: String) : VaultPinRecordReadResult
    data object Unavailable : VaultPinRecordReadResult
}

/**
 * Minimal persistence boundary for the derived Vault PIN verifier.
 *
 * Implementations must return false/[VaultPinRecordReadResult.Unavailable] on failure. They must
 * never downgrade the verifier to plaintext storage.
 */
interface VaultPinRecordStore {
    fun read(): VaultPinRecordReadResult
    fun write(record: String?): Boolean
}

/**
 * Dedicated Android Keystore-backed store for the non-reversible Vault PIN verifier.
 *
 * There is deliberately no ordinary [android.content.SharedPreferences] fallback. If Android
 * Keystore, encrypted preference creation, crypto, or disk IO fails, every operation fails closed.
 * The legacy encrypted credential file is opened directly (never through CredentialStore's
 * plaintext fallback) solely to migrate PIN records written by Jupiter 0.50.
 */
@Singleton
class AndroidKeystoreVaultPinRecordStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : VaultPinRecordStore {

    private val stores: Result<EncryptedStores> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        runCatching { createEncryptedStores() }
    }

    override fun read(): VaultPinRecordReadResult {
        val encryptedStores = stores.getOrNull() ?: return VaultPinRecordReadResult.Unavailable
        return when (val current = read(encryptedStores.vault, PIN_RECORD_KEY)) {
            is VaultPinRecordReadResult.Present -> current
            VaultPinRecordReadResult.Unavailable -> VaultPinRecordReadResult.Unavailable
            VaultPinRecordReadResult.Missing -> migrateLegacyRecord(encryptedStores)
        }
    }

    override fun write(record: String?): Boolean {
        val encryptedStores = stores.getOrNull() ?: return false
        return if (record == null) {
            // Remove both locations. If either commit fails, the caller keeps the fail-closed
            // "PIN configured" state; a surviving legacy record can therefore never weaken auth.
            commit(encryptedStores.vault, PIN_RECORD_KEY, null) &&
                commit(encryptedStores.legacy, LEGACY_PIN_RECORD_KEY, null)
        } else {
            val stored = commit(encryptedStores.vault, PIN_RECORD_KEY, record)
            if (stored) {
                // A stale legacy value is ignored whenever the dedicated record exists. Cleanup is
                // best-effort after the authoritative encrypted commit, avoiding data loss.
                commit(encryptedStores.legacy, LEGACY_PIN_RECORD_KEY, null)
            }
            stored
        }
    }

    private fun migrateLegacyRecord(stores: EncryptedStores): VaultPinRecordReadResult =
        when (val legacy = read(stores.legacy, LEGACY_PIN_RECORD_KEY)) {
            VaultPinRecordReadResult.Missing -> VaultPinRecordReadResult.Missing
            VaultPinRecordReadResult.Unavailable -> VaultPinRecordReadResult.Unavailable
            is VaultPinRecordReadResult.Present -> {
                if (!commit(stores.vault, PIN_RECORD_KEY, legacy.record)) {
                    VaultPinRecordReadResult.Unavailable
                } else {
                    // The new encrypted value is authoritative even if legacy cleanup fails.
                    commit(stores.legacy, LEGACY_PIN_RECORD_KEY, null)
                    legacy
                }
            }
        }

    private fun createEncryptedStores(): EncryptedStores {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedStores(
            vault = encryptedPreferences(VAULT_PREFS_NAME, masterKey),
            legacy = encryptedPreferences(LEGACY_ENCRYPTED_PREFS_NAME, masterKey),
        )
    }

    private fun encryptedPreferences(name: String, masterKey: MasterKey): SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            name,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun read(
        preferences: SharedPreferences,
        key: String,
    ): VaultPinRecordReadResult = try {
        preferences.getString(key, null)
            ?.let(VaultPinRecordReadResult::Present)
            ?: VaultPinRecordReadResult.Missing
    } catch (_: Throwable) {
        VaultPinRecordReadResult.Unavailable
    }

    private fun commit(
        preferences: SharedPreferences,
        key: String,
        value: String?,
    ): Boolean = try {
        val editor = preferences.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.commit()
    } catch (_: Throwable) {
        false
    }

    private data class EncryptedStores(
        val vault: SharedPreferences,
        val legacy: SharedPreferences,
    )

    private companion object {
        const val VAULT_PREFS_NAME = "jupiter_vault_pin"
        const val PIN_RECORD_KEY = "pin_verifier"

        // CredentialStore v0.50 keys. The plaintext fallback filename is intentionally absent.
        const val LEGACY_ENCRYPTED_PREFS_NAME = "jupiter_remote_credentials"
        const val LEGACY_PIN_RECORD_KEY = "secret_vault_pin_record"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultPinRecordStoreModule {
    @Binds
    @Singleton
    abstract fun bindVaultPinRecordStore(
        implementation: AndroidKeystoreVaultPinRecordStore,
    ): VaultPinRecordStore
}
