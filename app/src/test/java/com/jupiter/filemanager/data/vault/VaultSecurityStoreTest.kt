package com.jupiter.filemanager.data.vault

import com.jupiter.filemanager.data.preferences.VaultSecurityPreferencePolicy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecurityStoreTest {

    @Test
    fun `pin policy accepts only four to twelve digits`() {
        assertTrue(VaultSecurityPolicy.isValidPin("1234".toCharArray()))
        assertTrue(VaultSecurityPolicy.isValidPin("123456789012".toCharArray()))

        assertFalse(VaultSecurityPolicy.isValidPin("123".toCharArray()))
        assertFalse(VaultSecurityPolicy.isValidPin("1234567890123".toCharArray()))
        assertFalse(VaultSecurityPolicy.isValidPin("12a4".toCharArray()))
    }

    @Test
    fun `pbkdf record verifies correct pin and rejects incorrect pin`() {
        val record = VaultPinHasher.createRecord(
            pin = "5739".toCharArray(),
            salt = ByteArray(16) { index -> (index + 1).toByte() },
            iterations = 100_000,
        )

        assertFalse(record.contains("5739"))
        assertTrue(VaultPinHasher.isValidRecord(record))
        assertTrue(VaultPinHasher.verify("5739".toCharArray(), record))
        assertFalse(VaultPinHasher.verify("5738".toCharArray(), record))
    }

    @Test
    fun `different salts produce different verifier records for the same pin`() {
        val first = VaultPinHasher.createRecord(
            "5739".toCharArray(),
            ByteArray(16) { 1 },
            100_000,
        )
        val second = VaultPinHasher.createRecord(
            "5739".toCharArray(),
            ByteArray(16) { 2 },
            100_000,
        )

        assertFalse(first == second)
    }

    @Test
    fun `malformed or attacker-controlled pin records are rejected before hashing`() {
        assertFalse(VaultPinHasher.isValidRecord(""))
        assertFalse(VaultPinHasher.isValidRecord("v2\$100000\$bad\$bad"))
        assertFalse(VaultPinHasher.isValidRecord("v1\$999999999\$bad\$bad"))
        assertFalse(VaultPinHasher.verify("5739".toCharArray(), "not-a-record"))
    }

    @Test
    fun `biometrics can only be disabled when a fallback pin exists`() {
        assertFalse(VaultSecurityPolicy.canDisableBiometric(pinConfigured = false))
        assertTrue(VaultSecurityPolicy.canDisableBiometric(pinConfigured = true))
    }

    @Test
    fun `vault auto lock accepts only the supported safe windows`() {
        listOf(1, 5, 15, 30).forEach { value ->
            assertTrue(VaultSecurityPreferencePolicy.normalizeAutoLockMinutes(value) == value)
        }
        assertTrue(VaultSecurityPreferencePolicy.normalizeAutoLockMinutes(null) == 5)
        assertTrue(VaultSecurityPreferencePolicy.normalizeAutoLockMinutes(0) == 5)
        assertTrue(VaultSecurityPreferencePolicy.normalizeAutoLockMinutes(60) == 5)
    }

    @Test
    fun `record manager persists verifies and clears only through encrypted boundary`() = runTest {
        val store = FakeVaultPinRecordStore(VaultPinRecordReadResult.Missing)
        val manager = VaultPinRecordManager(store)

        assertFalse(manager.pinConfiguredFailClosed)
        val callerPin = "5739".toCharArray()
        assertEquals(VaultPinMutationResult.SUCCESS, manager.configurePin(callerPin))
        assertEquals("5739", String(callerPin))
        assertTrue(manager.pinConfiguredFailClosed)
        assertTrue(manager.hasUsablePinRecord())
        assertTrue(manager.verifyPin("5739".toCharArray()))
        assertFalse(manager.verifyPin("5738".toCharArray()))

        assertEquals(VaultPinMutationResult.SUCCESS, manager.clearPin())
        assertFalse(manager.pinConfiguredFailClosed)
        assertTrue(store.record == null)
    }

    @Test
    fun `unavailable encrypted storage is configured and unverifiable fail closed`() = runTest {
        val store = FakeVaultPinRecordStore(VaultPinRecordReadResult.Unavailable).apply {
            failWrites = true
        }
        val manager = VaultPinRecordManager(store)

        assertTrue(manager.pinConfiguredFailClosed)
        assertFalse(manager.hasUsablePinRecord())
        assertFalse(manager.verifyPin("5739".toCharArray()))
        assertEquals(
            VaultPinMutationResult.PERSISTENCE_FAILED,
            manager.configurePin("5739".toCharArray()),
        )
        assertEquals(VaultPinMutationResult.PERSISTENCE_FAILED, manager.clearPin())
        assertTrue(manager.pinConfiguredFailClosed)
    }

    @Test
    fun `malformed encrypted record remains configured but cannot authenticate`() = runTest {
        val manager = VaultPinRecordManager(
            FakeVaultPinRecordStore(VaultPinRecordReadResult.Present("tampered-record")),
        )

        assertTrue(manager.pinConfiguredFailClosed)
        assertFalse(manager.hasUsablePinRecord())
        assertFalse(manager.verifyPin("5739".toCharArray()))
    }

    private class FakeVaultPinRecordStore(
        initial: VaultPinRecordReadResult,
    ) : VaultPinRecordStore {
        var record: String? = (initial as? VaultPinRecordReadResult.Present)?.record
        var unavailable: Boolean = initial is VaultPinRecordReadResult.Unavailable
        var failWrites: Boolean = false

        override fun read(): VaultPinRecordReadResult = when {
            unavailable -> VaultPinRecordReadResult.Unavailable
            record == null -> VaultPinRecordReadResult.Missing
            else -> VaultPinRecordReadResult.Present(checkNotNull(record))
        }

        override fun write(record: String?): Boolean {
            if (failWrites || unavailable) return false
            this.record = record
            return true
        }
    }
}
