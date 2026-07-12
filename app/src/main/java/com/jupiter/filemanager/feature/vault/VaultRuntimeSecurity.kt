package com.jupiter.filemanager.feature.vault

import com.jupiter.filemanager.data.preferences.SettingsDataStore
import com.jupiter.filemanager.data.vault.VaultSecurityStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Narrow runtime-facing view of the persisted Vault authentication policy.
 *
 * Keeping this boundary separate from the screen makes it impossible for the UI to treat a
 * configured PIN as a boolean flag: an unlock attempt must call [verifyPin] and receive a real
 * verifier result. It also keeps the ViewModel independently testable without weakening or
 * replacing the encrypted [VaultSecurityStore].
 */
interface VaultRuntimeSecurity {
    val pinConfigured: StateFlow<Boolean>
    val biometricUnlockEnabled: Flow<Boolean>
    val autoLockMinutes: Flow<Int>

    suspend fun verifyPin(pin: CharArray): Boolean
}

@Singleton
class PersistedVaultRuntimeSecurity @Inject constructor(
    private val vaultSecurityStore: VaultSecurityStore,
    settingsDataStore: SettingsDataStore,
) : VaultRuntimeSecurity {
    override val pinConfigured: StateFlow<Boolean> = vaultSecurityStore.pinConfigured
    override val biometricUnlockEnabled: Flow<Boolean> = settingsDataStore.vaultBiometricLock
    override val autoLockMinutes: Flow<Int> = settingsDataStore.vaultAutoLockMinutes

    override suspend fun verifyPin(pin: CharArray): Boolean = vaultSecurityStore.verifyPin(pin)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultRuntimeSecurityModule {
    @Binds
    @Singleton
    abstract fun bindVaultRuntimeSecurity(
        implementation: PersistedVaultRuntimeSecurity,
    ): VaultRuntimeSecurity
}
