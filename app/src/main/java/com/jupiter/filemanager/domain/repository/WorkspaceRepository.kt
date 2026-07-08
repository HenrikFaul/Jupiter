package com.jupiter.filemanager.domain.repository

import com.jupiter.filemanager.domain.model.Workspace
import kotlinx.coroutines.flow.Flow

/**
 * Persists user-curated [Workspace]s (named collections of files and folders) and
 * exposes their current state.
 */
interface WorkspaceRepository {

    /**
     * Streams the current set of saved [Workspace]s, emitting on every change.
     */
    fun observeWorkspaces(): Flow<List<Workspace>>

    /**
     * Creates and persists a new workspace with the given [name] and initial
     * [itemPaths], returning the stable identifier of the created workspace.
     */
    suspend fun createWorkspace(name: String, itemPaths: List<String>): String

    /**
     * Returns the workspace identified by [id], or null if no such workspace exists.
     */
    suspend fun getWorkspace(id: String): Workspace?

    /**
     * Adds [path] to the workspace identified by [workspaceId]. If the path is
     * already a member of the workspace this is a no-op.
     */
    suspend fun addItem(workspaceId: String, path: String)

    /**
     * Permanently removes the workspace identified by [id], if present.
     */
    suspend fun deleteWorkspace(id: String)
}
