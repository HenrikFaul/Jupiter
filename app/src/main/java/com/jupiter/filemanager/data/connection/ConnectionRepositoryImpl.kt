package com.jupiter.filemanager.data.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jupiter.filemanager.data.remote.CredentialStore
import com.jupiter.filemanager.domain.model.CloudAccount
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import com.jupiter.filemanager.domain.repository.ConnectionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level [DataStore] delegate backing persisted [RemoteConnection]s and
 * linked [CloudAccount]s. Declared at file scope with a unique store name so the
 * single process-wide instance is honored, per the Preferences DataStore
 * contract, and kept distinct from the other feature stores.
 */
val Context.connectionsDataStore: DataStore<Preferences> by preferencesDataStore(name = "jupiter_connections")

/**
 * Preferences DataStore backed implementation of [ConnectionRepository].
 *
 * Both remote connections and cloud accounts are persisted as a [Set] of
 * pipe-delimited records, one entry per definition. The non-sensitive fields of a
 * remote (id/displayName/type/host/username/port/basePath) are persisted here;
 * the password is never stored in the entry and is instead delegated to the
 * encrypted [CredentialStore], keyed by the generated connection id. Removing a
 * remote also purges its stored secret.
 */
@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val credentialStore: CredentialStore,
) : ConnectionRepository {

    private val dataStore: DataStore<Preferences> = context.connectionsDataStore

    private object Keys {
        /** Set of `id|displayName|type|host|username|port|basePath` records, one per remote. */
        val REMOTES = stringSetPreferencesKey("remotes")

        /**
         * Set of
         * `id|provider|displayName|isConnected|usedBytes|totalBytes|accountEmail`
         * records, one per linked cloud account. Legacy 3-field
         * (`id|provider|displayName`) records still decode with defaulted tail.
         */
        val CLOUD_ACCOUNTS = stringSetPreferencesKey("cloud_accounts")
    }

    override fun observeRemotes(): Flow<List<RemoteConnection>> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.REMOTES].orEmpty()
                .mapNotNull { decodeRemote(it) }
                .sortedBy { it.displayName.lowercase() }
        }

    override suspend fun addRemote(
        displayName: String,
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ) {
        val name = displayName.trim()
        val trimmedHost = host.trim()
        if (name.isEmpty() || trimmedHost.isEmpty()) return
        val id = UUID.randomUUID().toString()
        val remote = RemoteConnection(
            id = id,
            displayName = name,
            type = type,
            host = trimmedHost,
            username = username?.trim()?.takeIf { it.isNotEmpty() },
            isOnline = false,
            port = port.coerceAtLeast(0),
            basePath = basePath?.trim()?.takeIf { it.isNotEmpty() },
        )
        // Persist the secret encrypted, keyed by the new connection id, before
        // the entry itself becomes observable.
        credentialStore.savePassword(id, password)
        dataStore.edit { prefs ->
            val current = prefs[Keys.REMOTES].orEmpty().toMutableSet()
            current.add(encodeRemote(remote))
            prefs[Keys.REMOTES] = current
        }
    }

    override suspend fun removeRemote(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.REMOTES].orEmpty()
            prefs[Keys.REMOTES] = current.filterNot { decodeRemote(it)?.id == id }.toSet()
        }
        credentialStore.deletePassword(id)
    }

    override fun observeCloudAccounts(): Flow<List<CloudAccount>> = dataStore.data
        .safe()
        .map { prefs ->
            prefs[Keys.CLOUD_ACCOUNTS].orEmpty()
                .mapNotNull { decodeCloudAccount(it) }
                .sortedBy { it.displayName.lowercase() }
        }

    override suspend fun addCloudAccount(provider: CloudProvider, displayName: String) {
        val name = displayName.trim()
        if (name.isEmpty()) return
        val account = CloudAccount(
            id = UUID.randomUUID().toString(),
            provider = provider,
            displayName = name,
            usedBytes = 0L,
            totalBytes = 0L,
            isConnected = false,
        )
        dataStore.edit { prefs ->
            val current = prefs[Keys.CLOUD_ACCOUNTS].orEmpty().toMutableSet()
            current.add(encodeCloudAccount(account))
            prefs[Keys.CLOUD_ACCOUNTS] = current
        }
    }

    override suspend fun updateCloudAccount(account: CloudAccount) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CLOUD_ACCOUNTS].orEmpty()
            // Only replace when a record with this id already exists, preserving
            // the no-op-on-missing contract.
            if (current.none { decodeCloudAccount(it)?.id == account.id }) return@edit
            val updated = current
                .filterNot { decodeCloudAccount(it)?.id == account.id }
                .toMutableSet()
            updated.add(encodeCloudAccount(account))
            prefs[Keys.CLOUD_ACCOUNTS] = updated
        }
    }

    override suspend fun removeCloudAccount(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.CLOUD_ACCOUNTS].orEmpty()
            prefs[Keys.CLOUD_ACCOUNTS] = current.filterNot { decodeCloudAccount(it)?.id == id }.toSet()
        }
    }

    /**
     * Swallows [IOException]s raised while reading the persisted file by emitting
     * empty preferences, so collectors fall back to empty state instead of
     * crashing.
     */
    private fun Flow<Preferences>.safe(): Flow<Preferences> = catch { throwable ->
        if (throwable is IOException) {
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    private companion object {
        private const val FIELD_DELIMITER = '|'

        /** Replaces record delimiters/newlines so a field round-trips safely. */
        private fun String.sanitize(): String =
            replace(FIELD_DELIMITER, ' ').replace('\n', ' ')

        /**
         * Encodes a [RemoteConnection] as
         * `id|displayName|type|host|username|port|basePath`.
         */
        fun encodeRemote(remote: RemoteConnection): String = buildString {
            append(remote.id)
            append(FIELD_DELIMITER)
            append(remote.displayName.sanitize())
            append(FIELD_DELIMITER)
            append(remote.type.name)
            append(FIELD_DELIMITER)
            append(remote.host.sanitize())
            append(FIELD_DELIMITER)
            append(remote.username?.sanitize().orEmpty())
            append(FIELD_DELIMITER)
            append(remote.port.toString())
            append(FIELD_DELIMITER)
            append(remote.basePath?.sanitize().orEmpty())
        }

        /**
         * Decodes a remote record, returning null when malformed.
         *
         * Tolerates the legacy 5-field format (`id|displayName|type|host|username`)
         * by defaulting port to 0 and basePath to null, so connections persisted
         * before the port/basePath fields existed still surface.
         */
        fun decodeRemote(record: String): RemoteConnection? {
            val parts = record.split(FIELD_DELIMITER)
            if (parts.size < 5) return null
            val id = parts[0]
            if (id.isEmpty()) return null
            val type = runCatching { ConnectionType.valueOf(parts[2]) }.getOrNull() ?: return null
            val username = parts[4].takeIf { it.isNotEmpty() }
            val port = parts.getOrNull(5)?.toIntOrNull() ?: 0
            val basePath = parts.getOrNull(6)?.takeIf { it.isNotEmpty() }
            return RemoteConnection(
                id = id,
                displayName = parts[1],
                type = type,
                host = parts[3],
                username = username,
                isOnline = false,
                port = port,
                basePath = basePath,
            )
        }

        /**
         * Encodes a [CloudAccount] as
         * `id|provider|displayName|isConnected|usedBytes|totalBytes|accountEmail`.
         */
        fun encodeCloudAccount(account: CloudAccount): String = buildString {
            append(account.id)
            append(FIELD_DELIMITER)
            append(account.provider.name)
            append(FIELD_DELIMITER)
            append(account.displayName.sanitize())
            append(FIELD_DELIMITER)
            append(account.isConnected.toString())
            append(FIELD_DELIMITER)
            append(account.usedBytes.toString())
            append(FIELD_DELIMITER)
            append(account.totalBytes.toString())
            append(FIELD_DELIMITER)
            append(account.accountEmail?.sanitize().orEmpty())
        }

        /**
         * Decodes a cloud account record, returning null when malformed.
         *
         * Tolerates the legacy 3-field format (`id|provider|displayName`) by
         * defaulting the connection/quota/email tail, so accounts persisted before
         * those fields existed still surface. An empty `accountEmail` field decodes
         * back to null.
         */
        fun decodeCloudAccount(record: String): CloudAccount? {
            val parts = record.split(FIELD_DELIMITER)
            if (parts.size < 3) return null
            val id = parts[0]
            if (id.isEmpty()) return null
            val provider = runCatching { CloudProvider.valueOf(parts[1]) }.getOrNull() ?: return null
            val isConnected = parts.getOrNull(3)?.toBooleanStrictOrNull() ?: false
            val usedBytes = parts.getOrNull(4)?.toLongOrNull() ?: 0L
            val totalBytes = parts.getOrNull(5)?.toLongOrNull() ?: 0L
            val accountEmail = parts.getOrNull(6)?.takeIf { it.isNotEmpty() }
            return CloudAccount(
                id = id,
                provider = provider,
                displayName = parts[2],
                usedBytes = usedBytes,
                totalBytes = totalBytes,
                isConnected = isConnected,
                accountEmail = accountEmail,
            )
        }
    }
}
