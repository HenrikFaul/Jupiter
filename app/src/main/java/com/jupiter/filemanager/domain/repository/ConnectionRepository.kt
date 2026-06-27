package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.CloudAccount
import com.jupiter.filemanager.domain.model.CloudProvider
import com.jupiter.filemanager.domain.model.ConnectionType
import com.jupiter.filemanager.domain.model.RemoteConnection
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-configured remote/network connections and linked cloud accounts.
 *
 * No live protocol I/O backend is wired yet; implementations persist the user's
 * connection definitions so they survive process death, while reachability and
 * real authentication remain honest "Connect"/"Coming soon" affordances at the UI.
 */
interface ConnectionRepository {

    /**
     * Streams the current set of user-configured [RemoteConnection]s, emitting on every change.
     */
    fun observeRemotes(): Flow<List<RemoteConnection>>

    /**
     * Adds a new remote connection definition.
     *
     * @param displayName human-readable name shown to the user.
     * @param type the protocol used to reach the host.
     * @param host the hostname or IP address of the remote server.
     * @param username optional username used for authentication.
     */
    suspend fun addRemote(
        displayName: String,
        type: ConnectionType,
        host: String,
        username: String?,
    )

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
     * Removes the cloud account identified by [id].
     */
    suspend fun removeCloudAccount(id: String)
}
