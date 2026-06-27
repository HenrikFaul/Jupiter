package com.jupiter.filemanager.data.connection

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
 * pipe-delimited records, one entry per definition. No live protocol or
 * authentication backend is wired yet, so persisted entries are always surfaced
 * with their reachability/connection flags off ([RemoteConnection.isOnline] and
 * [CloudAccount.isConnected] are `false`); the UI presents honest
 * "Connect"/"Coming soon" affordances rather than fabricating live state.
 */
@Singleton
class ConnectionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ConnectionRepository {

    private val dataStore: DataStore<Preferences> = context.connectionsDataStore

    private object Keys {
        /** Set of `id|displayName|type|host|username` records, one per remote. */
        val REMOTES = stringSetPreferencesKey("remotes")

        /** Set of `id|provider|displayName` records, one per linked cloud account. */
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
        username: String?,
    ) {
        val name = displayName.trim()
        val trimmedHost = host.trim()
        if (name.isEmpty() || trimmedHost.isEmpty()) return
        val remote = RemoteConnection(
            id = UUID.randomUUID().toString(),
            displayName = name,
            type = type,
            host = trimmedHost,
            username = username?.trim()?.takeIf { it.isNotEmpty() },
            isOnline = false,
        )
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

        /** Encodes a [RemoteConnection] as `id|displayName|type|host|username`. */
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
        }

        /** Decodes a remote record, returning null when malformed. */
        fun decodeRemote(record: String): RemoteConnection? {
            val parts = record.split(FIELD_DELIMITER)
            if (parts.size < 5) return null
            val id = parts[0]
            if (id.isEmpty()) return null
            val type = runCatching { ConnectionType.valueOf(parts[2]) }.getOrNull() ?: return null
            val username = parts[4].takeIf { it.isNotEmpty() }
            return RemoteConnection(
                id = id,
                displayName = parts[1],
                type = type,
                host = parts[3],
                username = username,
                isOnline = false,
            )
        }

        /** Encodes a [CloudAccount] as `id|provider|displayName`. */
        fun encodeCloudAccount(account: CloudAccount): String = buildString {
            append(account.id)
            append(FIELD_DELIMITER)
            append(account.provider.name)
            append(FIELD_DELIMITER)
            append(account.displayName.sanitize())
        }

        /** Decodes a cloud account record, returning null when malformed. */
        fun decodeCloudAccount(record: String): CloudAccount? {
            val parts = record.split(FIELD_DELIMITER)
            if (parts.size < 3) return null
            val id = parts[0]
            if (id.isEmpty()) return null
            val provider = runCatching { CloudProvider.valueOf(parts[1]) }.getOrNull() ?: return null
            return CloudAccount(
                id = id,
                provider = provider,
                displayName = parts[2],
                usedBytes = 0L,
                totalBytes = 0L,
                isConnected = false,
            )
        }
    }
}
