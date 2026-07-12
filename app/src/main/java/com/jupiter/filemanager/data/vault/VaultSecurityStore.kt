package com.jupiter.filemanager.data.vault

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Result of configuring or clearing the local Vault PIN. */
enum class VaultPinMutationResult {
    SUCCESS,
    INVALID_PIN,
    PERSISTENCE_FAILED,
}

/**
 * Stores and verifies the optional Vault PIN without ever persisting its plaintext value.
 *
 * The persisted record contains only a random salt, PBKDF2 iteration count, and derived hash.
 * [VaultPinRecordStore] protects that record with fail-closed Android Keystore-backed encrypted
 * storage. Biometric policy changes are coordinated here so biometrics cannot be disabled unless
 * a PIN record exists, and clearing the PIN first restores the safe biometric-on state.
 */
@Singleton
class VaultSecurityStore @Inject constructor(
    pinRecordStore: VaultPinRecordStore,
    private val settingsDataStore: SettingsDataStore,
) {

    private val pinRecordManager = VaultPinRecordManager(pinRecordStore)
    private val _pinConfigured = MutableStateFlow(pinRecordManager.pinConfiguredFailClosed)
    val pinConfigured: StateFlow<Boolean> = _pinConfigured.asStateFlow()

    fun hasPin(): Boolean = _pinConfigured.value

    suspend fun configurePin(pin: CharArray): VaultPinMutationResult {
        val result = pinRecordManager.configurePin(pin)
        _pinConfigured.value = pinRecordManager.pinConfiguredFailClosed
        return result
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val verified = pinRecordManager.verifyPin(pin)
        _pinConfigured.value = pinRecordManager.pinConfiguredFailClosed
        return verified
    }

    /**
     * Enables/disables biometric unlock while enforcing the fallback-credential invariant.
     * A request to disable biometrics without a configured PIN is rejected without mutation.
     */
    suspend fun setBiometricLock(enabled: Boolean): Boolean {
        if (!enabled) {
            val usablePinRecord = pinRecordManager.hasUsablePinRecord()
            _pinConfigured.value = pinRecordManager.pinConfiguredFailClosed
            if (!VaultSecurityPolicy.canDisableBiometric(usablePinRecord)) return false
        }
        settingsDataStore.setVaultBiometricLock(enabled)
        return true
    }

    /**
     * Clears the PIN only after biometric protection has been restored. If the settings write
     * fails, execution stops and the PIN record remains intact rather than weakening the Vault.
     */
    suspend fun clearPin(): VaultPinMutationResult {
        settingsDataStore.setVaultBiometricLock(true)
        val result = pinRecordManager.clearPin()
        _pinConfigured.value = pinRecordManager.pinConfiguredFailClosed
        return result
    }

}

/**
 * Testable PIN-verifier state machine. Missing is the only state treated as "no PIN"; corrupt or
 * unavailable encrypted storage remains configured and unverifiable so authentication fails closed.
 */
internal class VaultPinRecordManager(
    private val recordStore: VaultPinRecordStore,
) {
    private var recordState: VaultPinRecordReadResult = safeRead()

    val pinConfiguredFailClosed: Boolean
        get() = recordState !is VaultPinRecordReadResult.Missing

    suspend fun configurePin(pin: CharArray): VaultPinMutationResult {
        val transientPin = pin.copyOf()
        return try {
            if (!VaultSecurityPolicy.isValidPin(transientPin)) {
                VaultPinMutationResult.INVALID_PIN
            } else {
                val encoded = withContext(Dispatchers.Default) {
                    VaultPinHasher.createRecord(transientPin)
                }
                val written = withContext(Dispatchers.IO) { safeWrite(encoded) }
                recordState = withContext(Dispatchers.IO) { safeRead() }
                if (
                    written &&
                    (recordState as? VaultPinRecordReadResult.Present)?.record == encoded
                ) {
                    VaultPinMutationResult.SUCCESS
                } else {
                    VaultPinMutationResult.PERSISTENCE_FAILED
                }
            }
        } finally {
            transientPin.fill('\u0000')
        }
    }

    suspend fun verifyPin(pin: CharArray): Boolean {
        val transientPin = pin.copyOf()
        return try {
            if (!VaultSecurityPolicy.isValidPin(transientPin)) return false
            recordState = withContext(Dispatchers.IO) { safeRead() }
            val record = (recordState as? VaultPinRecordReadResult.Present)?.record
                ?.takeIf(VaultPinHasher::isValidRecord)
                ?: return false
            withContext(Dispatchers.Default) { VaultPinHasher.verify(transientPin, record) }
        } finally {
            transientPin.fill('\u0000')
        }
    }

    suspend fun clearPin(): VaultPinMutationResult {
        val removed = withContext(Dispatchers.IO) { safeWrite(null) }
        recordState = withContext(Dispatchers.IO) { safeRead() }
        return if (removed && recordState is VaultPinRecordReadResult.Missing) {
            VaultPinMutationResult.SUCCESS
        } else {
            VaultPinMutationResult.PERSISTENCE_FAILED
        }
    }

    suspend fun hasUsablePinRecord(): Boolean {
        recordState = withContext(Dispatchers.IO) { safeRead() }
        return (recordState as? VaultPinRecordReadResult.Present)
            ?.record
            ?.let(VaultPinHasher::isValidRecord)
            ?: false
    }

    private fun safeRead(): VaultPinRecordReadResult =
        runCatching(recordStore::read).getOrDefault(VaultPinRecordReadResult.Unavailable)

    private fun safeWrite(record: String?): Boolean =
        runCatching { recordStore.write(record) }.getOrDefault(false)
}

/** Pure validation and fallback-authentication policy, independently unit-testable. */
internal object VaultSecurityPolicy {
    private const val MIN_PIN_LENGTH = 4
    private const val MAX_PIN_LENGTH = 12

    fun isValidPin(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all { digit -> digit in '0'..'9' }

    fun canDisableBiometric(pinConfigured: Boolean): Boolean = pinConfigured
}

/** Versioned PBKDF2 codec for the non-reversible Vault PIN verifier. */
internal object VaultPinHasher {
    private const val VERSION = "v1"
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val DEFAULT_ITERATIONS = 210_000
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private const val SEPARATOR = '$'

    fun createRecord(pin: CharArray): String =
        createRecord(pin, randomSalt(), DEFAULT_ITERATIONS)

    internal fun createRecord(pin: CharArray, salt: ByteArray, iterations: Int): String {
        require(VaultSecurityPolicy.isValidPin(pin)) { "PIN does not meet Vault policy" }
        require(salt.size == SALT_BYTES) { "Unexpected salt size" }
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Unsafe PBKDF2 iteration count"
        }
        val hash = derive(pin, salt, iterations)
        return listOf(
            VERSION,
            iterations.toString(),
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash),
        ).joinToString(SEPARATOR.toString())
    }

    fun verify(pin: CharArray, record: String): Boolean {
        val parsed = parse(record) ?: return false
        val actual = derive(pin, parsed.salt, parsed.iterations)
        return MessageDigest.isEqual(parsed.hash, actual)
    }

    fun isValidRecord(record: String): Boolean = parse(record) != null

    private fun derive(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, HASH_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun parse(record: String): ParsedRecord? {
        val parts = record.split(SEPARATOR)
        if (parts.size != 4 || parts[0] != VERSION) return null
        val iterations = parts[1].toIntOrNull()
            ?.takeIf { it in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS }
            ?: return null
        val salt = decode(parts[2])?.takeIf { it.size == SALT_BYTES } ?: return null
        val hash = decode(parts[3])?.takeIf { it.size == HASH_BYTES } ?: return null
        return ParsedRecord(iterations, salt, hash)
    }

    private fun decode(value: String): ByteArray? =
        runCatching { Base64.getDecoder().decode(value) }.getOrNull()

    private fun randomSalt(): ByteArray = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)

    private data class ParsedRecord(
        val iterations: Int,
        val salt: ByteArray,
        val hash: ByteArray,
    )
}
