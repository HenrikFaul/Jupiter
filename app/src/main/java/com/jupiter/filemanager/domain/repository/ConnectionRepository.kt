package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.CloudAccount
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-configured remote/network connections and linked cloud accounts.
 *
 * Connection definitions survive process death. Secrets (passwords) are NOT stored
 * in the connection entry itself; implementations persist only the non-sensitive
 * fields (id/displayName/type/host/username/port/basePath) and delegate secret
 * storage to an encrypted credential store keyed by the generated connection id.
 */
interface ConnectionRepository {

    /**
     * Streams the current set of user-configured [RemoteConnection]s, emitting on every change.
     */
    fun observeRemotes(): Flow<List<RemoteConnection>>

    /**
     * Adds a new remote connection definition.
     *
     * The [password] is NOT persisted within the connection entry; implementations
     * store it via the encrypted credential store keyed by the newly generated
     * connection id, and persist only id/displayName/type/host/username/port/basePath.
     *
     * @param displayName human-readable name shown to the user.
     * @param type the protocol used to reach the host.
     * @param host the hostname or IP address of the remote server.
     * @param port the remote port (0 means "use the protocol default").
     * @param username optional username used for authentication.
     * @param password optional password used for authentication; stored encrypted, never in the entry.
     * @param basePath optional share name or base path to root the connection at.
     */
    suspend fun addRemote(
        displayName: String,
        type: ConnectionType,
        host: String,
        port: Int,
        username: String?,
        password: String?,
        basePath: String?,
    ): Boolean

    /**
     * Removes the remote connection identified by [id].
     */
    suspend fun removeRemote(id: String)

    /**
     * Streams the current set of linked [CloudAccount]s, emitting on every change.
     */
    fun observeCloudAccounts(): Flow<List<CloudAccount>>

    /**
     * Adds a new cloud account entry for the given [provider] and [displayName].
     */
    suspend fun addCloudAccount(provider: CloudProvider, displayName: String)

    /**
     * Replaces the persisted cloud account record sharing [account]'s id with the
     * given [account]. No-op if no record with that id currently exists. Used to
     * promote a placeholder entry to a connected one (real email/quota) and to
     * mutate connection state without changing the account's identity.
     */
    suspend fun updateCloudAccount(account: CloudAccount)

    /**
     * Removes the cloud account identified by [id].
     */
    suspend fun removeCloudAccount(id: String)
}
